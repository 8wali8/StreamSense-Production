package com.streamsense.chatservice.twitch;

import com.streamsense.chatservice.config.StreamSenseProperties;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class TwitchChatLifecycleService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TwitchChatLifecycleService.class);

    private final StreamSenseProperties.Chat properties;
    private final TwitchIrcMessageParser parser;
    private final TwitchChatMessageHandler handler;
    private final TwitchChatMetrics metrics;
    private final TwitchVodChatReplayService replayService;
    private volatile boolean running;
    private volatile Socket activeSocket;
    // The writer of the live connection, so one channel can be joined or parted without dropping the others.
    private volatile BufferedWriter activeWriter;
    private volatile List<String> liveChannels = List.of();
    private Thread worker;
    // Held around every write to the socket: the reader thread answers PINGs on it while a caller joins.
    private final Object sendLock = new Object();

    public TwitchChatLifecycleService(
            StreamSenseProperties streamSenseProperties,
            TwitchIrcMessageParser parser,
            TwitchChatMessageHandler handler,
            TwitchChatMetrics metrics,
            TwitchVodChatReplayService replayService) {
        this.properties = streamSenseProperties.getTwitch().getChat();
        this.parser = parser;
        this.handler = handler;
        this.metrics = metrics;
        this.replayService = replayService;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (!properties.isEnabled()) {
            metrics.markDisabled();
            log.info("Twitch chat ingestion disabled");
            return;
        }

        List<String> channels = normalizedChannels();
        if (channels.isEmpty()) {
            metrics.markStopped();
            log.info("Twitch chat ingestion enabled, waiting for a channel");
            return;
        }
        if (channels.size() > properties.getMaxChannels()) {
            throw new IllegalStateException("Twitch chat is configured with " + channels.size()
                    + " channels, more than max-channels (" + properties.getMaxChannels() + ")");
        }

        liveChannels = channels.stream()
                .filter(channel -> !replayService.isReplayChannel(channel))
                .toList();
        if (!liveChannels.isEmpty()) {
            validateCredentials();
        }

        List<String> replayChannels = replayService.start(channels);

        if (liveChannels.isEmpty()) {
            running = true;
            metrics.markConnected();
            log.info("Twitch VOD chat replay started channels={}", replayChannels);
            return;
        }
        running = true;
        worker = new Thread(this::runConnectorLoop, "twitch-chat-connector");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        replayService.stop();
        closeActiveSocket();
        if (worker != null) {
            worker.interrupt();
        }
        metrics.markStopped();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public synchronized TwitchChatStatus switchChannels(List<String> channels) {
        List<String> normalized = normalizeChannels(channels);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("at least one Twitch channel is required");
        }
        if (normalized.size() > properties.getMaxChannels()) {
            throw new IllegalArgumentException("at most " + properties.getMaxChannels() + " Twitch channels");
        }
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Twitch chat ingestion is disabled");
        }

        return restartWith(normalized);
    }

    /**
     * Joins one channel and leaves the others alone: what a streamer starting measurement of their own
     * channel calls. Idempotent. A live connector is sent a JOIN, so nobody else's chat is dropped; without
     * one (or for a replay alias, which is not an IRC channel) the connector restarts with the new list.
     */
    public synchronized TwitchChatStatus joinChannel(String channel) {
        String normalized = requireChannel(channel);
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Twitch chat ingestion is disabled");
        }
        List<String> current = normalizedChannels();
        if (current.contains(normalized)) {
            return metrics.snapshot();
        }
        if (current.size() >= properties.getMaxChannels()) {
            throw new IllegalStateException(
                    "Twitch chat ingestion is already measuring " + properties.getMaxChannels() + " channels");
        }

        List<String> next = append(current, normalized);
        if (running && !replayService.isReplayChannel(normalized) && sendToConnection("JOIN #" + normalized)) {
            liveChannels = append(liveChannels(), normalized);
            properties.setChannels(next);
            metrics.setChannels(next);
            log.info("joined Twitch chat channel={}", normalized);
            return metrics.snapshot();
        }
        return restartWith(next);
    }

    /** Parts one channel, leaving the others being measured alone. Idempotent. */
    public synchronized TwitchChatStatus partChannel(String channel) {
        String normalized = requireChannel(channel);
        List<String> current = normalizedChannels();
        if (!current.contains(normalized)) {
            return metrics.snapshot();
        }

        List<String> next = without(current, normalized);
        if (running
                && !next.isEmpty()
                && !replayService.isReplayChannel(normalized)
                && sendToConnection("PART #" + normalized)) {
            liveChannels = without(liveChannels(), normalized);
            properties.setChannels(next);
            metrics.setChannels(next);
            log.info("parted Twitch chat channel={}", normalized);
            return metrics.snapshot();
        }
        return restartWith(next);
    }

    /** Whether this channel is one of the channels being ingested. */
    public boolean isJoined(String channel) {
        return normalizedChannels().contains(requireChannel(channel));
    }

    private TwitchChatStatus restartWith(List<String> channels) {
        if (running) {
            stop();
        }
        properties.setChannels(channels);
        metrics.setChannels(channels);
        start();
        return metrics.snapshot();
    }

    /** Sends one line on the live connection; false when there is none or it failed, so the caller restarts. */
    private boolean sendToConnection(String line) {
        BufferedWriter writer = activeWriter;
        if (writer == null) {
            return false;
        }
        try {
            send(writer, line);
            return true;
        } catch (IOException e) {
            log.warn("Twitch chat command failed, restarting the connector: {}", e.getMessage());
            return false;
        }
    }

    private void runConnectorLoop() {
        long reconnectDelayMs = Math.max(1000, properties.getReconnectDelayMs());
        long maxReconnectDelayMs = Math.max(reconnectDelayMs, properties.getMaxReconnectDelayMs());

        while (running) {
            try {
                metrics.markConnecting();
                connectAndRead();
                reconnectDelayMs = Math.max(1000, properties.getReconnectDelayMs());
            } catch (Exception e) {
                metrics.markFailed(e.getMessage());
                log.warn("Twitch chat connector failed: {}", e.getMessage());
            } finally {
                closeActiveSocket();
            }

            if (running) {
                metrics.markReconnecting();
                sleepBeforeReconnect(reconnectDelayMs);
                reconnectDelayMs = Math.min(maxReconnectDelayMs, reconnectDelayMs * 2);
            }
        }
    }

    private void connectAndRead() throws IOException {
        try (Socket socket = openSocket();
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer =
                        new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            activeSocket = socket;
            authenticateAndJoin(writer);
            // Only now: a JOIN sent before PASS, NICK, and CAP would be refused, and the caller would
            // have been told the channel was joined. Until this point joinChannel restarts instead.
            activeWriter = writer;
            metrics.markConnected();
            log.info("Twitch chat connector connected channels={}", liveChannels());

            String line;
            while (running && (line = reader.readLine()) != null) {
                handleLine(line, writer);
            }

            if (running) {
                throw new IOException("Twitch IRC connection closed");
            }
        } finally {
            activeWriter = null;
        }
    }

    private Socket openSocket() throws IOException {
        if (properties.isSsl()) {
            Socket rawSocket = new Socket();
            rawSocket.connect(
                    new InetSocketAddress(properties.getHost(), properties.getPort()),
                    properties.getConnectionTimeoutMs());
            SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket)
                    sslSocketFactory.createSocket(rawSocket, properties.getHost(), properties.getPort(), true);
            sslSocket.startHandshake();
            return sslSocket;
        }

        Socket socket = new Socket();
        socket.connect(
                new InetSocketAddress(properties.getHost(), properties.getPort()), properties.getConnectionTimeoutMs());
        return socket;
    }

    private void authenticateAndJoin(BufferedWriter writer) throws IOException {
        send(writer, "PASS " + normalizedOauthToken());
        send(writer, "NICK " + properties.getUsername().trim());
        send(writer, "CAP REQ :twitch.tv/tags twitch.tv/commands");
        for (String channel : liveChannels()) {
            send(writer, "JOIN #" + channel);
        }
    }

    private void handleLine(String line, BufferedWriter writer) throws IOException {
        if (parser.isPing(line)) {
            send(writer, "PONG :tmi.twitch.tv");
            return;
        }

        try {
            parser.parseChatMessage(line, Instant.now().toEpochMilli()).ifPresent(handler::handle);
        } catch (RuntimeException e) {
            metrics.recordParseFailure();
            log.warn("failed to parse Twitch IRC line type={} message={}", lineType(line), e.getMessage());
        }
    }

    private void send(BufferedWriter writer, String line) throws IOException {
        synchronized (sendLock) {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }
    }

    private void validateCredentials() {
        if (isBlank(properties.getUsername())) {
            throw new IllegalStateException("Twitch chat is enabled but username is missing");
        }
        if (isBlank(properties.getOauthToken())) {
            throw new IllegalStateException("Twitch chat is enabled but OAuth token is missing");
        }
    }

    private String normalizedOauthToken() {
        String token = properties.getOauthToken().trim();
        return token.startsWith("oauth:") ? token : "oauth:" + token;
    }

    private List<String> normalizedChannels() {
        return normalizeChannels(properties.getChannels());
    }

    private List<String> liveChannels() {
        return liveChannels == null ? List.of() : liveChannels;
    }

    /** One channel, normalised the way the list is; blank is a client error. */
    private static String requireChannel(String channel) {
        List<String> normalized = normalizeChannels(channel == null ? List.of() : List.of(channel));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("a Twitch channel is required");
        }
        return normalized.get(0);
    }

    private static List<String> append(List<String> channels, String channel) {
        return Stream.concat(channels.stream(), Stream.of(channel)).toList();
    }

    private static List<String> without(List<String> channels, String channel) {
        return channels.stream().filter(existing -> !existing.equals(channel)).toList();
    }

    private static List<String> normalizeChannels(List<String> channels) {
        if (channels == null) {
            return List.of();
        }
        return channels.stream()
                .filter(channel -> channel != null && !channel.isBlank())
                .map(channel -> channel.trim().toLowerCase(Locale.ROOT))
                // The console and the gateway both carry a login with a leading @ or # at times.
                .map(channel -> channel.replaceFirst("^[@#]+", ""))
                .filter(channel -> !channel.isBlank())
                .toList();
    }

    private void sleepBeforeReconnect(long reconnectDelayMs) {
        try {
            Thread.sleep(reconnectDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeActiveSocket() {
        Socket socket = activeSocket;
        activeSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("error closing Twitch chat socket", e);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String lineType(String line) {
        if (line == null || line.isBlank()) {
            return "blank";
        }
        if (line.startsWith("@")) {
            int commandStart = line.indexOf(" PRIVMSG ");
            return commandStart >= 0 ? "PRIVMSG" : "tagged";
        }
        int space = line.indexOf(' ');
        return space < 0 ? line : line.substring(0, space);
    }
}

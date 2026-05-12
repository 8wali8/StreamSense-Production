package com.streamsense.chatservice.twitch;

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

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.streamsense.chatservice.config.StreamSenseProperties;

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
    private volatile List<String> liveChannels = List.of();
    private Thread worker;

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
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Twitch chat ingestion is disabled");
        }

        if (running) {
            stop();
        }

        properties.setChannels(normalized);
        metrics.setChannels(normalized);
        start();

        return metrics.snapshot();
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
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            activeSocket = socket;
            authenticateAndJoin(writer);
            metrics.markConnected();
            log.info("Twitch chat connector connected channels={}", liveChannels());

            String line;
            while (running && (line = reader.readLine()) != null) {
                handleLine(line, writer);
            }

            if (running) {
                throw new IOException("Twitch IRC connection closed");
            }
        }
    }

    private Socket openSocket() throws IOException {
        if (properties.isSsl()) {
            Socket rawSocket = new Socket();
            rawSocket.connect(new InetSocketAddress(properties.getHost(), properties.getPort()), properties.getConnectionTimeoutMs());
            SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslSocketFactory
                    .createSocket(rawSocket, properties.getHost(), properties.getPort(), true);
            sslSocket.startHandshake();
            return sslSocket;
        }

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(properties.getHost(), properties.getPort()), properties.getConnectionTimeoutMs());
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

    private static void send(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
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

    private static List<String> normalizeChannels(List<String> channels) {
        if (channels == null) {
            return List.of();
        }
        return channels.stream()
                .filter(channel -> channel != null && !channel.isBlank())
                .map(channel -> channel.trim().toLowerCase(Locale.ROOT))
                .map(channel -> channel.startsWith("#") ? channel.substring(1) : channel)
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

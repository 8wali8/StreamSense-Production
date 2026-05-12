package com.streamsense.chatservice.twitch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.streamsense.chatservice.config.StreamSenseProperties;
import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.service.ChatEventIngestService;

@Component
public class TwitchVodChatReplayService {

    private static final Logger log = LoggerFactory.getLogger(TwitchVodChatReplayService.class);

    private final StreamSenseProperties.Replay replayProperties;
    private final TwitchVodCommentClient commentClient;
    private final ChatEventIngestService ingestService;
    private final TwitchChatMetrics metrics;
    private final Map<String, ReplayWorker> workers = new ConcurrentHashMap<>();

    public TwitchVodChatReplayService(
            StreamSenseProperties properties,
            TwitchVodCommentClient commentClient,
            ChatEventIngestService ingestService,
            TwitchChatMetrics metrics) {
        this.replayProperties = properties.getReplay();
        this.commentClient = commentClient;
        this.ingestService = ingestService;
        this.metrics = metrics;
    }

    public synchronized List<String> start(List<String> channels) {
        stop();
        List<String> replayChannels = new ArrayList<>();
        for (String channel : channels) {
            StreamSenseProperties.ReplayAlias alias = aliasFor(channel);
            if (alias == null) {
                continue;
            }
            ReplayWorker worker = new ReplayWorker(channel, alias);
            workers.put(channel, worker);
            worker.start();
            replayChannels.add(channel);
        }
        return replayChannels;
    }

    public synchronized void stop() {
        for (ReplayWorker worker : workers.values()) {
            worker.stop();
        }
        workers.clear();
    }

    public boolean isReplayChannel(String channel) {
        return aliasFor(channel) != null;
    }

    private StreamSenseProperties.ReplayAlias aliasFor(String channel) {
        if (channel == null || replayProperties.getAliases() == null) {
            return null;
        }
        return replayProperties.getAliases().get(normalize(channel));
    }

    private static String normalize(String channel) {
        String value = channel.trim().toLowerCase(Locale.ROOT);
        while (value.startsWith("#") || value.startsWith("@")) {
            value = value.substring(1);
        }
        return value;
    }

    private final class ReplayWorker implements Runnable {
        private final String channel;
        private final StreamSenseProperties.ReplayAlias alias;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private Thread thread;

        private ReplayWorker(String channel, StreamSenseProperties.ReplayAlias alias) {
            this.channel = channel;
            this.alias = alias;
        }

        private void start() {
            thread = new Thread(this, "twitch-vod-chat-replay-" + channel);
            thread.setDaemon(true);
            thread.start();
        }

        private void stop() {
            running.set(false);
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public void run() {
            try {
                if (!"twitch".equalsIgnoreCase(alias.getProvider())) {
                    throw new IllegalStateException("unsupported replay provider: " + alias.getProvider());
                }
                List<TwitchVodChatComment> comments = commentClient.fetchComments(alias);
                if (comments.isEmpty()) {
                    log.warn("Twitch VOD chat replay has no comments channel={} vodId={}", channel, alias.getVodId());
                    return;
                }

                long loop = 0;
                do {
                    replayComments(comments, loop);
                    loop++;
                } while (running.get() && alias.isLoop());
            } catch (RuntimeException e) {
                metrics.markFailed(e.getMessage());
                log.warn("Twitch VOD chat replay failed channel={} vodId={} error={}", channel, alias.getVodId(), e.getMessage());
            }
        }

        private void replayComments(List<TwitchVodChatComment> comments, long loop) {
            long replayStartedAt = Instant.now().toEpochMilli();
            for (TwitchVodChatComment comment : comments) {
                if (!running.get()) {
                    return;
                }
                if (comment.offsetSeconds() < alias.getStartOffsetSeconds()) {
                    continue;
                }
                long dueAt = replayStartedAt + Math.round(
                        ((comment.offsetSeconds() - alias.getStartOffsetSeconds()) / alias.getReplaySpeed()) * 1000.0);
                sleepUntil(dueAt);
                if (!running.get()) {
                    return;
                }
                ChatMessageEvent event = new ChatMessageEvent(
                        "vod-" + alias.getVodId() + "-loop-" + loop + "-chat-" + comment.id(),
                        channel,
                        comment.user(),
                        comment.message(),
                        Instant.now().toEpochMilli());
                ingestService.ingestTwitch(event);
                metrics.recordMessage();
            }
        }

        private void sleepUntil(long dueAt) {
            long delayMs = dueAt - Instant.now().toEpochMilli();
            while (running.get() && delayMs > 0) {
                try {
                    Thread.sleep(Math.min(delayMs, 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running.set(false);
                    return;
                }
                delayMs = dueAt - Instant.now().toEpochMilli();
            }
        }
    }
}

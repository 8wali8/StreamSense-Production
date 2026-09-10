package com.streamsense.chatservice.twitch;

import com.streamsense.chatservice.api.VodChatImportRequest;
import com.streamsense.chatservice.api.VodChatImportStatus;
import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.service.ChatEventIngestService;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Imports a recording's chat: every comment Twitch still has for the VOD is published through the
 * normal ingest path at its original time ({@code baseTimeMs} plus the comment's offset), tagged
 * with the importer's session key and {@code TWITCH_VOD_IMPORT} as source. Event ids are derived
 * from the video and comment ids, so importing twice is idempotent downstream. Comments are
 * published page by page as Twitch returns them and never held as a whole, so a busy recording
 * costs the memory of one page, not of every comment. The status's {@code total} therefore grows
 * with the import and equals {@code published} when it is done. One import runs per recording at
 * a time; the rest of the service is untouched by it.
 */
@Service
public class TwitchVodChatImportService {

    public static final String SOURCE = "TWITCH_VOD_IMPORT";

    private static final Logger log = LoggerFactory.getLogger(TwitchVodChatImportService.class);

    private final TwitchVodCommentClient commentClient;
    private final ChatEventIngestService ingestService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vod-chat-import");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, VodChatImportStatus> imports = new ConcurrentHashMap<>();

    public TwitchVodChatImportService(TwitchVodCommentClient commentClient, ChatEventIngestService ingestService) {
        this.commentClient = commentClient;
        this.ingestService = ingestService;
    }

    public VodChatImportStatus start(VodChatImportRequest request) {
        String vodId = request.vodId().trim();
        String channel = request.channel().trim().toLowerCase(Locale.ROOT).replaceFirst("^[@#]+", "");
        VodChatImportStatus current = imports.get(vodId);
        if (current != null && ("QUEUED".equals(current.state()) || "RUNNING".equals(current.state()))) {
            throw new IllegalStateException("chat import for VOD " + vodId + " is already running");
        }
        VodChatImportStatus queued = status(vodId, channel, "QUEUED", 0, 0, null);
        imports.put(vodId, queued);
        executor.submit(() -> run(
                channel, vodId, request.baseTimeMs(), request.streamSessionId().trim()));
        return queued;
    }

    public Optional<VodChatImportStatus> status(String vodId) {
        return Optional.ofNullable(imports.get(vodId));
    }

    void run(String channel, String vodId, long baseTimeMs, String streamSessionId) {
        try {
            imports.put(vodId, status(vodId, channel, "RUNNING", 0, 0, null));
            int[] published = {0};
            commentClient.forEachPage(vodId, 0, page -> {
                for (TwitchVodChatComment comment : page) {
                    ChatMessageEvent event = new ChatMessageEvent(
                            "vod-" + vodId + "-import-" + comment.id(),
                            channel,
                            comment.user(),
                            comment.message(),
                            baseTimeMs + Math.round(comment.offsetSeconds() * 1000.0));
                    event.setSource(SOURCE);
                    event.setChannelLogin(channel);
                    event.setStreamSessionId(streamSessionId);
                    event.setTwitchStreamId(vodId);
                    ingestService.ingestTwitch(event);
                    published[0]++;
                }
                imports.put(vodId, status(vodId, channel, "RUNNING", published[0], published[0], null));
            });
            imports.put(vodId, status(vodId, channel, "DONE", published[0], published[0], null));
            log.info("VOD chat import finished vod={} channel={} comments={}", vodId, channel, published[0]);
        } catch (RuntimeException ex) {
            log.warn("VOD chat import failed vod={} channel={} error={}", vodId, channel, ex.getMessage());
            VodChatImportStatus last = imports.get(vodId);
            imports.put(
                    vodId,
                    status(
                            vodId,
                            channel,
                            "FAILED",
                            last == null ? 0 : last.published(),
                            last == null ? 0 : last.total(),
                            ex.getMessage()));
        }
    }

    private static VodChatImportStatus status(
            String vodId, String channel, String state, int published, int total, String error) {
        return new VodChatImportStatus(
                vodId, channel, state, published, total, error, Instant.now().toEpochMilli());
    }
}

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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * <p>An import can be stopped by its recording: the page loop checks a per-recording flag between
 * pages, so a stop lands within one page of comments, and a later request for the same recording
 * carries the offset reached to page from there instead of from the start.
 */
@Service
public class TwitchVodChatImportService {

    public static final String SOURCE = "TWITCH_VOD_IMPORT";

    private static final Logger log = LoggerFactory.getLogger(TwitchVodChatImportService.class);

    private final TwitchVodCommentClient commentClient;
    private final ChatEventIngestService ingestService;
    private final Executor executor;
    private final Map<String, VodChatImportStatus> imports = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> stops = new ConcurrentHashMap<>();

    @Autowired
    public TwitchVodChatImportService(TwitchVodCommentClient commentClient, ChatEventIngestService ingestService) {
        this(commentClient, ingestService, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vod-chat-import");
            thread.setDaemon(true);
            return thread;
        }));
    }

    TwitchVodChatImportService(
            TwitchVodCommentClient commentClient, ChatEventIngestService ingestService, Executor executor) {
        this.commentClient = commentClient;
        this.ingestService = ingestService;
        this.executor = executor;
    }

    public VodChatImportStatus start(VodChatImportRequest request) {
        String vodId = request.vodId().trim();
        String channel = request.channel().trim().toLowerCase(Locale.ROOT).replaceFirst("^[@#]+", "");
        VodChatImportStatus current = imports.get(vodId);
        if (current != null && isActive(current)) {
            throw new IllegalStateException("chat import for VOD " + vodId + " is already running");
        }
        long startOffset = request.startOffsetOrZero();
        AtomicBoolean stop = new AtomicBoolean(false);
        stops.put(vodId, stop);
        VodChatImportStatus queued = status(vodId, channel, "QUEUED", 0, 0, startOffset, false, null);
        imports.put(vodId, queued);
        executor.execute(() -> run(
                channel, vodId, request.baseTimeMs(), request.streamSessionId().trim(), startOffset));
        return queued;
    }

    public Optional<VodChatImportStatus> status(String vodId) {
        return Optional.ofNullable(imports.get(vodId));
    }

    /**
     * Stops this recording's import and no other. A queued import ends at once; a running one after the
     * page it is on. Empty when nothing is known about the recording; an import that already ended is
     * returned unchanged.
     */
    public Optional<VodChatImportStatus> stop(String vodId) {
        VodChatImportStatus current = imports.get(vodId);
        if (current == null) {
            return Optional.empty();
        }
        if (!isActive(current)) {
            return Optional.of(current);
        }
        AtomicBoolean stop = stops.get(vodId);
        if (stop != null) {
            stop.set(true);
        }
        // A queued import never reaches its thread's stop check; settle it here so the caller sees STOPPED.
        VodChatImportStatus updated = imports.computeIfPresent(
                vodId,
                (id, latest) -> "QUEUED".equals(latest.state())
                        ? withState(latest, "STOPPED", true)
                        : withState(latest, latest.state(), true));
        return Optional.ofNullable(updated);
    }

    void run(String channel, String vodId, long baseTimeMs, String streamSessionId, long startOffsetSeconds) {
        AtomicBoolean stop = stops.computeIfAbsent(vodId, id -> new AtomicBoolean(false));
        VodChatImportStatus queued = imports.get(vodId);
        if (queued != null && !"QUEUED".equals(queued.state())) {
            // Stopped while it waited its turn.
            return;
        }
        double[] offset = {startOffsetSeconds};
        int[] published = {0};
        try {
            imports.put(vodId, status(vodId, channel, "RUNNING", 0, 0, offset[0], false, null));
            commentClient.forEachPage(
                    vodId,
                    startOffsetSeconds,
                    page -> {
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
                            offset[0] = Math.max(offset[0], comment.offsetSeconds());
                        }
                        imports.put(
                                vodId,
                                status(
                                        vodId,
                                        channel,
                                        "RUNNING",
                                        published[0],
                                        published[0],
                                        offset[0],
                                        stop.get(),
                                        null));
                    },
                    stop::get);
            if (stop.get()) {
                imports.put(
                        vodId, status(vodId, channel, "STOPPED", published[0], published[0], offset[0], true, null));
                log.info(
                        "VOD chat import stopped vod={} channel={} comments={} at offset={}s",
                        vodId,
                        channel,
                        published[0],
                        offset[0]);
                return;
            }
            imports.put(vodId, status(vodId, channel, "DONE", published[0], published[0], offset[0], false, null));
            log.info("VOD chat import finished vod={} channel={} comments={}", vodId, channel, published[0]);
        } catch (RuntimeException ex) {
            log.warn("VOD chat import failed vod={} channel={} error={}", vodId, channel, ex.getMessage());
            imports.put(
                    vodId,
                    status(
                            vodId,
                            channel,
                            "FAILED",
                            published[0],
                            published[0],
                            offset[0],
                            stop.get(),
                            ex.getMessage()));
        }
    }

    private static boolean isActive(VodChatImportStatus status) {
        return "QUEUED".equals(status.state()) || "RUNNING".equals(status.state());
    }

    private static VodChatImportStatus withState(VodChatImportStatus status, String state, boolean stopRequested) {
        return new VodChatImportStatus(
                status.vodId(),
                status.channel(),
                state,
                status.published(),
                status.total(),
                status.offsetSeconds(),
                stopRequested,
                status.lastError(),
                Instant.now().toEpochMilli());
    }

    private static VodChatImportStatus status(
            String vodId,
            String channel,
            String state,
            int published,
            int total,
            double offsetSeconds,
            boolean stopRequested,
            String error) {
        return new VodChatImportStatus(
                vodId,
                channel,
                state,
                published,
                total,
                offsetSeconds,
                stopRequested,
                error,
                Instant.now().toEpochMilli());
    }
}

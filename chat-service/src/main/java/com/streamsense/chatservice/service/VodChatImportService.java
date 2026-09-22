package com.streamsense.chatservice.service;

import com.streamsense.chatservice.api.VodChatImportRequest;
import com.streamsense.chatservice.api.VodChatImportStatus;
import com.streamsense.chatservice.api.VodChatLine;
import com.streamsense.chatservice.events.ChatMessageEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
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
 * Imports a recording's chat from the lines a streamer supplied: each is published through the normal
 * ingest path at its original time ({@code baseTimeMs} plus the line's offset), tagged with the
 * importer's session key and {@code TWITCH_VOD_IMPORT} as source. Event ids are derived from the
 * recording and the line's own content, so importing the same log twice is idempotent downstream.
 * Twitch is never asked: it offers no download of a recording's chat and refuses automated access to
 * its own replay, so a log the streamer has is the only source. One import runs per recording at a
 * time; the rest of the service is untouched by it.
 *
 * <p>An import can be stopped by its recording: the loop checks a per-recording flag between pages of
 * lines, so a stop lands within one page, and a later request for the same recording carries the offset
 * reached to continue from there.
 */
@Service
public class VodChatImportService {

    public static final String SOURCE = "TWITCH_VOD_IMPORT";

    /** Lines published between two looks at the stop flag and the status. */
    static final int PAGE = 200;

    private static final Logger log = LoggerFactory.getLogger(VodChatImportService.class);

    private final ChatEventIngestService ingestService;
    private final Executor executor;
    private final Map<String, VodChatImportStatus> imports = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> stops = new ConcurrentHashMap<>();

    @Autowired
    public VodChatImportService(ChatEventIngestService ingestService) {
        this(ingestService, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vod-chat-import");
            thread.setDaemon(true);
            return thread;
        }));
    }

    VodChatImportService(ChatEventIngestService ingestService, Executor executor) {
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
        stops.put(vodId, new AtomicBoolean(false));
        VodChatImportStatus queued =
                status(vodId, channel, "QUEUED", 0, request.comments().size(), startOffset, false, null);
        imports.put(vodId, queued);
        List<VodChatLine> lines = List.copyOf(request.comments());
        executor.execute(() -> run(
                channel, vodId, request.baseTimeMs(), request.streamSessionId().trim(), startOffset, lines));
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

    void run(
            String channel,
            String vodId,
            long baseTimeMs,
            String streamSessionId,
            long startOffsetSeconds,
            List<VodChatLine> lines) {
        AtomicBoolean stop = stops.computeIfAbsent(vodId, id -> new AtomicBoolean(false));
        VodChatImportStatus queued = imports.get(vodId);
        if (queued != null && !"QUEUED".equals(queued.state())) {
            // Stopped while it waited its turn.
            return;
        }
        double offset = startOffsetSeconds;
        int published = 0;
        int total = lines.size();
        try {
            imports.put(vodId, status(vodId, channel, "RUNNING", 0, total, offset, false, null));
            Map<String, Integer> seen = new HashMap<>();
            for (int from = 0; from < total; from += PAGE) {
                if (stop.get()) {
                    break;
                }
                for (VodChatLine line : lines.subList(from, Math.min(total, from + PAGE))) {
                    if (line.offsetSeconds() < startOffsetSeconds) {
                        continue;
                    }
                    ChatMessageEvent event = new ChatMessageEvent(
                            "vod-" + vodId + "-log-" + lineId(line, seen),
                            channel,
                            line.user(),
                            line.message(),
                            baseTimeMs + Math.round(line.offsetSeconds() * 1000.0));
                    event.setSource(SOURCE);
                    event.setChannelLogin(channel);
                    event.setStreamSessionId(streamSessionId);
                    event.setTwitchStreamId(vodId);
                    ingestService.ingestTwitch(event);
                    published++;
                    offset = Math.max(offset, line.offsetSeconds());
                }
                imports.put(vodId, status(vodId, channel, "RUNNING", published, total, offset, stop.get(), null));
            }
            if (stop.get()) {
                imports.put(vodId, status(vodId, channel, "STOPPED", published, total, offset, true, null));
                log.info(
                        "VOD chat import stopped vod={} channel={} lines={} of {} at offset={}s",
                        vodId,
                        channel,
                        published,
                        total,
                        offset);
                return;
            }
            imports.put(vodId, status(vodId, channel, "DONE", published, total, offset, false, null));
            log.info("VOD chat import finished vod={} channel={} lines={}", vodId, channel, published);
        } catch (RuntimeException ex) {
            log.warn("VOD chat import failed vod={} channel={} error={}", vodId, channel, ex.getMessage());
            imports.put(vodId, status(vodId, channel, "FAILED", published, total, offset, stop.get(), ex.getMessage()));
        }
    }

    /**
     * A stable id for a line: the same log imported again lands on the same events. Two identical lines
     * in one log (the same second, user, and text) are told apart by a counter.
     */
    static String lineId(VodChatLine line, Map<String, Integer> seen) {
        String key = Math.round(line.offsetSeconds() * 1000.0) + "|" + line.user() + "|" + line.message();
        String digest = sha256(key).substring(0, 16);
        int repeat = seen.merge(digest, 1, Integer::sum);
        return repeat == 1 ? digest : digest + "-" + repeat;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
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

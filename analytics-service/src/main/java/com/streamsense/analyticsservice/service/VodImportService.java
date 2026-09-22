package com.streamsense.analyticsservice.service;

import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.api.VodChatLogSummary;
import com.streamsense.analyticsservice.api.VodImport;
import com.streamsense.analyticsservice.api.VodImportStatus;
import com.streamsense.analyticsservice.api.VodListing;
import com.streamsense.analyticsservice.imports.CaptureReplayClient;
import com.streamsense.analyticsservice.imports.ChatLogParser;
import com.streamsense.analyticsservice.imports.ChatReplayClient;
import com.streamsense.analyticsservice.imports.ReplayStatus;
import com.streamsense.analyticsservice.model.ChatLogLine;
import com.streamsense.analyticsservice.model.StreamSessionRow;
import com.streamsense.analyticsservice.model.VodChatLogRow;
import com.streamsense.analyticsservice.model.VodImportRow;
import com.streamsense.analyticsservice.persistence.StreamSessionRepository;
import com.streamsense.analyticsservice.persistence.VodChatLogRepository;
import com.streamsense.analyticsservice.persistence.VodImportRepository;
import com.streamsense.analyticsservice.twitch.HelixVideo;
import com.streamsense.analyticsservice.twitch.TwitchHelixClient;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Importing a channel's past broadcasts from their Twitch recordings, for a streamer who joins with
 * a deal already running. The recording's metadata becomes a session with Twitch's bounds; its chat
 * and its frames and audio are replayed through the normal pipeline with the original timestamps,
 * so every number is computed the same way as for a live stream.
 *
 * <p>This service owns the import's state. chat-service and video-capture-service each run their half
 * and keep its status in memory, which a restart wipes; the row here ({@code vod_imports}) is what the
 * console reads and what a stop or a resume is decided from. It is refreshed from both services while
 * an import is active, on a schedule and whenever a channel's imports are asked for. A stop is
 * fanned out to both halves and the import reads STOPPING until each has confirmed; a later import
 * request for a stopped or failed recording resumes both halves from the offset the slower one
 * reached, which is safe because every replayed event id is deterministic.
 */
@Service
public class VodImportService {

    private static final Logger log = LoggerFactory.getLogger(VodImportService.class);

    /** The chat half's state when no log was supplied: nothing to replay, nothing failed, nothing to wait for. */
    public static final String CHAT_NONE = "NONE";

    private final ObjectProvider<TwitchHelixClient> helix;
    private final ObjectProvider<ChatReplayClient> chatReplay;
    private final ObjectProvider<CaptureReplayClient> captureReplay;
    private final StreamSessionService sessions;
    private final StreamSessionRepository sessionRows;
    private final VodImportRepository imports;
    private final VodChatLogRepository chatLogs;
    private final ChatLogParser chatLogParser;
    private final Clock clock;

    @Autowired
    public VodImportService(
            ObjectProvider<TwitchHelixClient> helix,
            ObjectProvider<ChatReplayClient> chatReplay,
            ObjectProvider<CaptureReplayClient> captureReplay,
            StreamSessionService sessions,
            StreamSessionRepository sessionRows,
            VodImportRepository imports,
            VodChatLogRepository chatLogs,
            ChatLogParser chatLogParser) {
        this(
                helix,
                chatReplay,
                captureReplay,
                sessions,
                sessionRows,
                imports,
                chatLogs,
                chatLogParser,
                Clock.systemUTC());
    }

    VodImportService(
            ObjectProvider<TwitchHelixClient> helix,
            ObjectProvider<ChatReplayClient> chatReplay,
            ObjectProvider<CaptureReplayClient> captureReplay,
            StreamSessionService sessions,
            StreamSessionRepository sessionRows,
            VodImportRepository imports,
            VodChatLogRepository chatLogs,
            ChatLogParser chatLogParser,
            Clock clock) {
        this.helix = helix;
        this.chatReplay = chatReplay;
        this.captureReplay = captureReplay;
        this.sessions = sessions;
        this.sessionRows = sessionRows;
        this.imports = imports;
        this.chatLogs = chatLogs;
        this.chatLogParser = chatLogParser;
        this.clock = clock;
    }

    /** The channel's recordings, newest first, each with the session it was imported into if any. */
    public List<VodListing> list(String streamer, int limit) {
        String login = login(streamer);
        List<VodListing> result = new ArrayList<>();
        Map<String, VodChatLogRow> logs = new HashMap<>();
        for (VodChatLogRow row : chatLogs.findByStreamer(login)) {
            logs.put(row.vodId(), row);
        }
        for (HelixVideo video : requireHelix().archives(login, limit)) {
            Long sessionId = sessionRows
                    .findByVodId(login, video.id())
                    .map(row -> row.id())
                    .orElse(null);
            VodChatLogRow logRow = logs.get(video.id());
            result.add(new VodListing(
                    video.id(),
                    video.streamId(),
                    video.title(),
                    video.createdAt(),
                    video.durationMs(),
                    video.url(),
                    video.viewCount(),
                    sessionId,
                    logRow == null ? null : summary(logRow)));
        }
        return result;
    }

    /** Starts an import, or resumes a stopped or failed one from the offset both halves had reached. */
    public VodImport importVod(String streamer, String vodId, Integer averageViewers) {
        String login = login(streamer);
        Optional<VodImportRow> existing = imports.find(login, vodId);
        if (existing.isPresent() && existing.get().isActive()) {
            throw new IllegalStateException("recording " + vodId + " is already being imported");
        }
        HelixVideo video = requireHelix()
                .video(vodId)
                .orElseThrow(() -> new IllegalArgumentException("Twitch has no recording with id " + vodId));
        if (!video.userLogin().isBlank() && !video.userLogin().equals(login)) {
            throw new IllegalArgumentException("recording " + vodId + " belongs to @" + video.userLogin());
        }
        if (video.durationMs() <= 0) {
            throw new IllegalArgumentException("recording " + vodId + " has no duration yet");
        }
        String key = streamSessionId(login, vodId);
        rejectIfAnalyzedLive(login, vodId, key, video.createdAt(), video.createdAt() + video.durationMs());
        StreamSession session = sessions.recordVod(
                login,
                video.id(),
                video.streamId(),
                video.title(),
                video.createdAt(),
                video.durationMs(),
                key,
                averageViewers);
        // Each half resumes from where it got to, not from the import's combined offset: a half that failed
        // early must not send the other back to the start, and ids are deterministic so overlap is harmless.
        Optional<VodImportRow> resumable = existing.filter(VodImportRow::isResumable);
        long chatStart = resumable.map(VodImportRow::chatOffsetSeconds).orElse(0L);
        long captureStart = resumable.map(VodImportRow::captureOffsetSeconds).orElse(0L);

        List<String> problems = new ArrayList<>();
        // The chat half comes from a log the streamer supplied, or there is none: Twitch offers no download
        // of a recording's chat and refuses automated access to its own replay.
        List<ChatLogLine> lines = chatLogs.lines(login, vodId);
        boolean chatStarted = false;
        String chatState = CHAT_NONE;
        if (!lines.isEmpty()) {
            chatStarted = startChat(login, video, key, chatStart, lines, problems);
            chatState = chatStarted ? ReplayStatus.QUEUED : ReplayStatus.FAILED;
        }
        boolean captureStarted = false;
        CaptureReplayClient capture = captureReplay.getIfAvailable();
        if (capture == null) {
            problems.add("capture replay is not configured (streamsense.services.video-capture-service.base-url)");
        } else {
            try {
                capture.replay(
                        login, video.id(), video.url(), video.createdAt(), video.durationMs(), key, captureStart);
                captureStarted = true;
            } catch (RuntimeException ex) {
                log.warn("capture replay could not start vod={} : {}", vodId, ex.getMessage());
                problems.add("capture replay could not start: " + ex.getMessage());
            }
        }
        long now = clock.millis();
        boolean chatOk = chatStarted || lines.isEmpty();
        String started = chatOk && captureStarted ? VodImportRow.QUEUED : VodImportRow.FAILED;
        VodImportRow row = new VodImportRow(
                login,
                vodId,
                session.id(),
                started,
                lines.isEmpty() ? captureStart : Math.min(chatStart, captureStart),
                video.durationMs() / 1000,
                chatState,
                chatStart,
                captureStarted ? ReplayStatus.QUEUED : ReplayStatus.FAILED,
                captureStart,
                problems.isEmpty() ? null : truncate(String.join("; ", problems)),
                now,
                now);
        imports.save(row);
        return new VodImport(session, key, chatStarted, captureStarted, problems, toStatus(row));
    }

    private boolean startChat(
            String login,
            HelixVideo video,
            String key,
            long chatStart,
            List<ChatLogLine> lines,
            List<String> problems) {
        ChatReplayClient chat = chatReplay.getIfAvailable();
        if (chat == null) {
            problems.add("chat replay is not configured (streamsense.services.chat-service.base-url)");
            return false;
        }
        try {
            chat.replay(login, video.id(), video.createdAt(), key, chatStart, lines);
            return true;
        } catch (RuntimeException ex) {
            log.warn("chat replay could not start vod={} : {}", video.id(), ex.getMessage());
            problems.add("chat replay could not start: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Keeps a chat log for the recording, replacing any earlier one. An import that is running without
     * chat starts its chat half from the log at once; a finished or stopped one gets it on Resume.
     *
     * @param zone the zone a text log's wall-clock times are in (the streamer's browser)
     */
    public VodChatLogSummary uploadChatLog(
            String streamer, String vodId, String fileName, ZoneId zone, String content) {
        String login = login(streamer);
        HelixVideo video = requireHelix()
                .video(vodId)
                .orElseThrow(() -> new IllegalArgumentException("Twitch has no recording with id " + vodId));
        if (!video.userLogin().isBlank() && !video.userLogin().equals(login)) {
            throw new IllegalArgumentException("recording " + vodId + " belongs to @" + video.userLogin());
        }
        List<ChatLogLine> lines = chatLogParser.parse(content, video.createdAt(), video.durationMs(), zone);
        long now = clock.millis();
        VodChatLogRow saved = chatLogs.save(login, vodId, fileName, lines, now);
        log.info("chat log kept for vod={} streamer={} lines={} file={}", vodId, login, lines.size(), fileName);

        Optional<VodImportRow> row = imports.find(login, vodId);
        if (row.isPresent()
                && row.get().isActive()
                && CHAT_NONE.equals(row.get().chatState())) {
            List<String> problems = new ArrayList<>();
            boolean started = startChat(login, video, streamSessionId(login, vodId), 0, lines, problems);
            VodImportRow current = row.get();
            imports.save(new VodImportRow(
                    current.streamer(),
                    current.vodId(),
                    current.sessionId(),
                    current.state(),
                    current.offsetSeconds(),
                    current.durationSeconds(),
                    started ? ReplayStatus.QUEUED : ReplayStatus.FAILED,
                    0,
                    current.captureState(),
                    current.captureOffsetSeconds(),
                    problems.isEmpty() ? current.lastError() : truncate(String.join("; ", problems)),
                    current.requestedAt(),
                    now));
        }
        return summary(saved);
    }

    private static VodChatLogSummary summary(VodChatLogRow row) {
        return new VodChatLogSummary(
                row.fileName(), row.lineCount(), row.firstOffsetSeconds(), row.lastOffsetSeconds(), row.uploadedAt());
    }

    /**
     * Stops one recording's import: both halves are told, and the import reads STOPPING until each has
     * confirmed. Stopping an import that is not running answers its current status and changes nothing.
     */
    public VodImportStatus stop(String streamer, String vodId) {
        String login = login(streamer);
        VodImportRow row = imports.find(login, vodId)
                .orElseThrow(() -> new IllegalArgumentException("no import of recording " + vodId + " to stop"));
        if (!row.isActive()) {
            return toStatus(row);
        }
        VodImportRow stopping = row.withState(VodImportRow.STOPPING, clock.millis());
        imports.save(stopping);
        return toStatus(refresh(stopping));
    }

    /** Every import the channel has asked for, the active ones brought up to date first. */
    public List<VodImportStatus> statuses(String streamer) {
        String login = login(streamer);
        List<VodImportStatus> result = new ArrayList<>();
        for (VodImportRow row : imports.findByStreamer(login)) {
            result.add(toStatus(row.isActive() ? refresh(row) : row));
        }
        return result;
    }

    /** Brings every active import up to date, so a stop or a finish is recorded even when nobody is watching. */
    @Scheduled(fixedDelayString = "${streamsense.analytics.vod-import-refresh-ms:5000}")
    public void refreshActiveImports() {
        for (VodImportRow row : imports.findActive()) {
            try {
                refresh(row);
            } catch (RuntimeException ex) {
                log.warn(
                        "could not refresh import vod={} streamer={}: {}",
                        row.vodId(),
                        row.streamer(),
                        ex.getMessage());
            }
        }
    }

    /**
     * Asks both services where the import stands and records the answer. A stop still pending is sent
     * to whichever half is still running. A half the service no longer knows about (it restarted) is
     * failed, never resumed on its own: the streamer decides with Resume.
     */
    private VodImportRow refresh(VodImportRow row) {
        boolean stopping = VodImportRow.STOPPING.equals(row.state());
        Half chat = half(
                row.chatState(),
                row.chatOffsetSeconds(),
                stopping,
                chatReplay.getIfAvailable(),
                client -> client.status(row.vodId()),
                client -> client.stop(row.vodId()),
                "chat-service");
        Half capture = half(
                row.captureState(),
                row.captureOffsetSeconds(),
                stopping,
                captureReplay.getIfAvailable(),
                client -> client.status(row.vodId()),
                client -> client.stop(row.vodId()),
                "video-capture-service");
        String state = combined(stopping, chat, capture);
        long offset = combinedOffset(chat, capture, row.durationSeconds());
        String error = capture.error() != null ? capture.error() : chat.error();
        if (error == null && !VodImportRow.FAILED.equals(state)) {
            error = null;
        } else if (error == null) {
            error = row.lastError();
        }
        VodImportRow updated = new VodImportRow(
                row.streamer(),
                row.vodId(),
                row.sessionId(),
                state,
                Math.max(row.offsetSeconds(), Math.min(offset, row.durationSeconds())),
                row.durationSeconds(),
                chat.state(),
                chat.offset(),
                capture.state(),
                capture.offset(),
                truncate(error),
                row.requestedAt(),
                clock.millis());
        if (!updated.state().equals(row.state())) {
            log.info(
                    "import vod={} streamer={} {} -> {} at offset={}s (chat {}, capture {})",
                    row.vodId(),
                    row.streamer(),
                    row.state(),
                    updated.state(),
                    updated.offsetSeconds(),
                    chat.state(),
                    capture.state());
        }
        imports.save(updated);
        return updated;
    }

    private static <C> Half half(
            String lastState,
            long lastOffset,
            boolean stopping,
            C client,
            Function<C, Optional<ReplayStatus>> status,
            Function<C, Optional<ReplayStatus>> stop,
            String serviceName) {
        boolean wasActive = ReplayStatus.QUEUED.equals(lastState) || ReplayStatus.RUNNING.equals(lastState);
        if (client == null || !wasActive) {
            // Never started there, or already settled: nothing to ask.
            return new Half(lastState, lastOffset, null);
        }
        Optional<ReplayStatus> answer;
        try {
            answer = status.apply(client);
            if (stopping
                    && answer.map(ReplayStatus::isActive).orElse(false)
                    && !answer.get().stopRequested()) {
                answer = stop.apply(client).or(() -> Optional.empty());
            }
        } catch (RuntimeException ex) {
            // Unreachable for the moment: keep what was last known and try again on the next refresh.
            log.debug("{} did not answer for the import: {}", serviceName, ex.getMessage());
            return new Half(lastState, lastOffset, null);
        }
        if (answer.isEmpty()) {
            return new Half(
                    ReplayStatus.FAILED,
                    lastOffset,
                    serviceName + " lost track of the import (restarted?); resume to continue from " + lastOffset
                            + "s");
        }
        ReplayStatus reported = answer.get();
        return new Half(reported.state(), Math.max(lastOffset, reported.offsetSeconds()), reported.lastError());
    }

    private static String combined(boolean stopping, Half chat, Half capture) {
        boolean anyActive = chat.isActive() || capture.isActive();
        if (anyActive) {
            if (stopping) {
                return VodImportRow.STOPPING;
            }
            boolean bothQueued =
                    ReplayStatus.QUEUED.equals(chat.state()) && ReplayStatus.QUEUED.equals(capture.state());
            return bothQueued ? VodImportRow.QUEUED : VodImportRow.IMPORTING;
        }
        if (stopping || ReplayStatus.STOPPED.equals(chat.state()) || ReplayStatus.STOPPED.equals(capture.state())) {
            return VodImportRow.STOPPED;
        }
        if (ReplayStatus.FAILED.equals(chat.state()) || ReplayStatus.FAILED.equals(capture.state())) {
            return VodImportRow.FAILED;
        }
        return VodImportRow.DONE;
    }

    /**
     * How far the import as a whole has got: the slower of the halves that have not failed, so a half that
     * fell over early does not pin the label at that point while the other works on. The failed half is
     * named by its own state and the error; a resume takes each half's own offset regardless.
     */
    private static long combinedOffset(Half chat, Half capture, long durationSeconds) {
        long chatReached = chat.reached(durationSeconds);
        long captureReached = capture.reached(durationSeconds);
        if (chat.isFailed() == capture.isFailed()) {
            return Math.min(chatReached, captureReached);
        }
        return chat.isFailed() ? captureReached : chatReached;
    }

    /** One half of an import as last reported: its state, the offset it reached, and its error if any. */
    private record Half(String state, long offset, String error) {

        boolean isActive() {
            return ReplayStatus.QUEUED.equals(state) || ReplayStatus.RUNNING.equals(state);
        }

        boolean isFailed() {
            return ReplayStatus.FAILED.equals(state);
        }

        /** Where this half has got to: a finished half, or one with nothing to do, counts as the whole recording. */
        long reached(long durationSeconds) {
            return ReplayStatus.DONE.equals(state) || CHAT_NONE.equals(state) ? durationSeconds : offset;
        }
    }

    private static VodImportStatus toStatus(VodImportRow row) {
        return new VodImportStatus(
                row.streamer(),
                row.vodId(),
                row.sessionId(),
                row.state(),
                row.offsetSeconds(),
                row.durationSeconds(),
                row.chatState(),
                row.captureState(),
                row.lastError(),
                row.updatedAt());
    }

    private static String truncate(String error) {
        return error == null || error.length() <= 512 ? error : error.substring(0, 512);
    }

    /** The streamSessionId every replayed event of a recording carries. */
    static String streamSessionId(String login, String vodId) {
        return login + "-vod-" + vodId;
    }

    /**
     * A capture session overlapping the recording's window means the broadcast's chat and frames
     * were already analyzed live, and the reports sum every session in a window: replaying the
     * recording on top would count that stream twice. Importing the same recording again is fine
     * (its events carry the same ids and are deduplicated); a live capture is not.
     */
    private void rejectIfAnalyzedLive(String login, String vodId, String key, long from, long to) {
        long now = System.currentTimeMillis();
        for (StreamSessionRow row : sessionRows.findByStreamer(login, from, to, 50)) {
            boolean liveCapture = StreamSessionService.SOURCE_CAPTURE.equals(row.source());
            // An open capture session reaches at least as far as its last event.
            long end = Math.max(row.endOr(now), row.lastSeenAt());
            boolean overlaps = row.startedAt() < to && end > from;
            if (liveCapture && overlaps && !key.equals(row.streamSessionId())) {
                throw new IllegalStateException(
                        "recording " + vodId + " overlaps a stream that was analyzed live (session " + row.id()
                                + "); importing it would count that stream twice");
            }
        }
    }

    private TwitchHelixClient requireHelix() {
        TwitchHelixClient client = helix.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException(
                    "Twitch Helix is disabled (streamsense.twitch.helix.enabled); recordings cannot be listed");
        }
        return client;
    }

    private static String login(String streamer) {
        String cleaned = streamer == null
                ? ""
                : streamer.trim().replaceFirst("^[@#]+", "").toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("streamer is required");
        }
        return cleaned;
    }
}

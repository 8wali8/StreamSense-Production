package com.streamsense.analyticsservice.service;

import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.api.VodImport;
import com.streamsense.analyticsservice.api.VodListing;
import com.streamsense.analyticsservice.imports.CaptureReplayClient;
import com.streamsense.analyticsservice.imports.ChatReplayClient;
import com.streamsense.analyticsservice.model.StreamSessionRow;
import com.streamsense.analyticsservice.persistence.StreamSessionRepository;
import com.streamsense.analyticsservice.twitch.HelixVideo;
import com.streamsense.analyticsservice.twitch.TwitchHelixClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Importing a channel's past broadcasts from their Twitch recordings, for a streamer who joins with
 * a deal already running. The recording's metadata becomes a session with Twitch's bounds; its chat
 * and its frames and audio are replayed through the normal pipeline with the original timestamps,
 * so every number is computed the same way as for a live stream.
 */
@Service
public class VodImportService {

    private static final Logger log = LoggerFactory.getLogger(VodImportService.class);

    private final ObjectProvider<TwitchHelixClient> helix;
    private final ObjectProvider<ChatReplayClient> chatReplay;
    private final ObjectProvider<CaptureReplayClient> captureReplay;
    private final StreamSessionService sessions;
    private final StreamSessionRepository sessionRows;

    public VodImportService(
            ObjectProvider<TwitchHelixClient> helix,
            ObjectProvider<ChatReplayClient> chatReplay,
            ObjectProvider<CaptureReplayClient> captureReplay,
            StreamSessionService sessions,
            StreamSessionRepository sessionRows) {
        this.helix = helix;
        this.chatReplay = chatReplay;
        this.captureReplay = captureReplay;
        this.sessions = sessions;
        this.sessionRows = sessionRows;
    }

    /** The channel's recordings, newest first, each with the session it was imported into if any. */
    public List<VodListing> list(String streamer, int limit) {
        String login = login(streamer);
        List<VodListing> result = new ArrayList<>();
        for (HelixVideo video : requireHelix().archives(login, limit)) {
            Long sessionId = sessionRows
                    .findByVodId(login, video.id())
                    .map(row -> row.id())
                    .orElse(null);
            result.add(new VodListing(
                    video.id(),
                    video.streamId(),
                    video.title(),
                    video.createdAt(),
                    video.durationMs(),
                    video.url(),
                    video.viewCount(),
                    sessionId));
        }
        return result;
    }

    public VodImport importVod(String streamer, String vodId, Integer averageViewers) {
        String login = login(streamer);
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

        List<String> problems = new ArrayList<>();
        boolean chatStarted = false;
        ChatReplayClient chat = chatReplay.getIfAvailable();
        if (chat == null) {
            problems.add("chat replay is not configured (streamsense.services.chat-service.base-url)");
        } else {
            try {
                chat.replay(login, video.id(), video.createdAt(), key);
                chatStarted = true;
            } catch (RuntimeException ex) {
                log.warn("chat replay could not start vod={} : {}", vodId, ex.getMessage());
                problems.add("chat replay could not start: " + ex.getMessage());
            }
        }
        boolean captureStarted = false;
        CaptureReplayClient capture = captureReplay.getIfAvailable();
        if (capture == null) {
            problems.add("capture replay is not configured (streamsense.services.video-capture-service.base-url)");
        } else {
            try {
                capture.replay(login, video.id(), video.url(), video.createdAt(), video.durationMs(), key);
                captureStarted = true;
            } catch (RuntimeException ex) {
                log.warn("capture replay could not start vod={} : {}", vodId, ex.getMessage());
                problems.add("capture replay could not start: " + ex.getMessage());
            }
        }
        return new VodImport(session, key, chatStarted, captureStarted, problems);
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

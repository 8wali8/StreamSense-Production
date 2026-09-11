package com.streamsense.analyticsservice.service;

import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.model.StreamSessionRow;
import com.streamsense.analyticsservice.model.ViewerSample;
import com.streamsense.analyticsservice.persistence.StreamSessionRepository;
import com.streamsense.analyticsservice.twitch.HelixStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stream sessions: a Twitch broadcast seen by the Helix poller, or a capture run derived from the
 * events' {@code streamSessionId} when the poller is off or the channel is a replay alias. When
 * both exist for the same broadcast the Helix one is reported and the overlapping capture one hidden.
 */
@Service
public class StreamSessionService {

    public static final String SOURCE_HELIX = "HELIX";
    public static final String SOURCE_CAPTURE = "CAPTURE";
    public static final String SOURCE_VOD = "VOD";

    private static final Logger log = LoggerFactory.getLogger(StreamSessionService.class);
    private static final int MAX_LIMIT = 200;

    private final StreamSessionRepository sessions;
    private final StreamSenseProperties properties;
    private final Clock clock;

    @Autowired
    public StreamSessionService(StreamSessionRepository sessions, StreamSenseProperties properties) {
        this(sessions, properties, Clock.systemUTC());
    }

    StreamSessionService(StreamSessionRepository sessions, StreamSenseProperties properties, Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    /** Called for every aggregated event: opens or extends the capture session the event belongs to. */
    @Transactional
    public void recordActivity(
            String streamer, String streamSessionId, String twitchStreamId, String channelLogin, long eventAt) {
        if (streamSessionId == null || streamSessionId.isBlank()) {
            return;
        }
        String key = streamSessionId.trim();
        long now = clock.millis();
        Optional<StreamSessionRow> existing = sessions.find(streamer, SOURCE_CAPTURE, key);
        if (existing.isEmpty()) {
            // Events replayed from a recording carry the imported session's key; its bounds come from Twitch.
            if (sessions.findByStreamSessionId(streamer, key).isPresent()) {
                return;
            }
            sessions.insert(
                    streamer,
                    SOURCE_CAPTURE,
                    key,
                    twitchStreamId,
                    key,
                    channelLogin,
                    null,
                    null,
                    eventAt,
                    eventAt,
                    now);
            return;
        }
        StreamSessionRow row = existing.get();
        long startedAt = Math.min(row.startedAt(), eventAt);
        long lastSeenAt = Math.max(row.lastSeenAt(), eventAt);
        if (startedAt != row.startedAt() || lastSeenAt != row.lastSeenAt() || !row.isOpen()) {
            sessions.touch(row.id(), startedAt, lastSeenAt, now);
        }
    }

    /**
     * A recording being imported: the session its replayed events will land in. A Helix session of the
     * same broadcast is reused; otherwise a closed VOD session with Twitch's bounds is created. The
     * streamer's viewer figure, when given, becomes the session's one viewer sample.
     */
    @Transactional
    public StreamSession recordVod(
            String streamer,
            String vodId,
            String streamId,
            String title,
            long createdAt,
            long durationMs,
            String streamSessionId,
            Integer averageViewers) {
        long now = clock.millis();
        String login = streamer.toLowerCase(Locale.ROOT);
        Optional<StreamSessionRow> existing = sessions.findByVodId(login, vodId);
        if (existing.isEmpty() && streamId != null) {
            existing = sessions.find(login, SOURCE_HELIX, streamId);
        }
        long id;
        if (existing.isPresent()) {
            id = existing.get().id();
            sessions.attachVod(id, vodId, streamSessionId, title, now);
        } else {
            id = sessions.insert(
                    login, SOURCE_VOD, vodId, streamId, streamSessionId, login, title, null, createdAt, createdAt, now);
            sessions.attachVod(id, vodId, streamSessionId, title, now);
            sessions.close(id, createdAt + Math.max(0, durationMs), now);
        }
        StreamSessionRow row = sessions.findById(id).orElseThrow();
        if (averageViewers != null && row.viewerSamples() == 0) {
            sessions.addViewerSample(id, row.startedAt(), averageViewers, now);
        }
        return get(id).orElseThrow();
    }

    /** A Helix poll saw this broadcast live: open it if new, and record the viewer sample. */
    @Transactional
    public void recordHelixLive(HelixStream stream) {
        long now = clock.millis();
        String streamer = stream.userLogin().toLowerCase(Locale.ROOT);
        long id = sessions.find(streamer, SOURCE_HELIX, stream.id())
                .map(StreamSessionRow::id)
                .orElseGet(() -> sessions.insert(
                        streamer,
                        SOURCE_HELIX,
                        stream.id(),
                        stream.id(),
                        null,
                        streamer,
                        stream.title(),
                        stream.gameName(),
                        stream.startedAt(),
                        now,
                        now));
        sessions.recordLive(id, stream.title(), stream.gameName(), stream.viewerCount(), now, now);
    }

    /** After a poll: every open Helix session of a watched channel that was not live is over. */
    @Transactional
    public int closeHelixSessionsNotLive(Collection<String> watched, Collection<String> liveStreamIds) {
        long now = clock.millis();
        Set<String> watchedLower =
                watched.stream().map(login -> login.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> live = Set.copyOf(liveStreamIds);
        int closed = 0;
        for (StreamSessionRow open : sessions.findOpen(SOURCE_HELIX)) {
            if (watchedLower.contains(open.streamer()) && !live.contains(open.sessionRef())) {
                sessions.close(open.id(), Math.max(open.lastSeenAt(), open.startedAt()), now);
                closed++;
            }
        }
        return closed;
    }

    /** Capture sessions end when their events stop; the poller never sees a replay alias go offline. */
    @Scheduled(fixedDelayString = "${streamsense.analytics.capture-session-close-check-ms:60000}")
    @Transactional
    public int closeIdleCaptureSessions() {
        long now = clock.millis();
        long idleMs = properties.getAnalytics().getCaptureSessionIdleCloseMinutes() * 60_000L;
        int closed = sessions.closeIdle(SOURCE_CAPTURE, now - idleMs, now);
        if (closed > 0) {
            log.info("closed {} idle capture session(s)", closed);
        }
        return closed;
    }

    public List<ViewerSample> viewerSamples(long sessionId) {
        return sessions.findViewerSamples(sessionId);
    }

    public List<String> streamersSeenSince(long since) {
        return sessions.findStreamersSeenSince(since);
    }

    public List<StreamSession> list(String streamer, Long from, Long to, Integer requestedLimit) {
        String cleaned = clean(streamer);
        if (cleaned == null) {
            throw new IllegalArgumentException("streamer is required");
        }
        if (from != null && to != null && from >= to) {
            throw new IllegalArgumentException("from must be before to");
        }
        int limit = requestedLimit == null ? 20 : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        // Over-fetch so hidden capture sessions do not shrink the page below the limit.
        return visible(sessions.findByStreamer(cleaned, from, to, Math.min(MAX_LIMIT * 2, limit * 4)), limit);
    }

    /** Every session in a range, the same hiding of captured duplicates as {@link #list}, no page cap. */
    public List<StreamSession> listAll(String streamer, Long from, Long to) {
        String cleaned = clean(streamer);
        if (cleaned == null) {
            throw new IllegalArgumentException("streamer is required");
        }
        return visible(sessions.findAllByStreamer(cleaned, from, to), Integer.MAX_VALUE);
    }

    /** Capture sessions that overlap a Helix or VOD session are the same broadcast seen twice; show it once. */
    private List<StreamSession> visible(List<StreamSessionRow> rows, int limit) {
        long now = clock.millis();
        List<StreamSessionRow> helix = rows.stream()
                .filter(row -> SOURCE_HELIX.equals(row.source()) || SOURCE_VOD.equals(row.source()))
                .toList();
        List<StreamSession> result = new ArrayList<>();
        for (StreamSessionRow row : rows) {
            if (SOURCE_CAPTURE.equals(row.source()) && overlapsAny(row, helix, now)) {
                continue;
            }
            result.add(toApi(row, now));
            if (result.size() == limit) {
                break;
            }
        }
        return result;
    }

    public Optional<StreamSession> get(long id) {
        long now = clock.millis();
        return sessions.findById(id).map(row -> toApi(row, now));
    }

    private boolean overlapsAny(StreamSessionRow capture, List<StreamSessionRow> helix, long now) {
        long captureEnd = capture.endOr(now);
        return helix.stream().anyMatch(h -> capture.startedAt() < h.endOr(now) && captureEnd > h.startedAt());
    }

    private StreamSession toApi(StreamSessionRow row, long now) {
        long end = row.endOr(now);
        Double average = row.viewerSamples() == 0 ? null : (double) row.viewerSum() / row.viewerSamples();
        return new StreamSession(
                row.id(),
                row.streamer(),
                row.source(),
                row.twitchStreamId(),
                row.streamSessionId(),
                row.channelLogin(),
                row.title(),
                row.category(),
                row.startedAt(),
                row.endedAt(),
                row.isOpen(),
                Math.max(0, end - row.startedAt()),
                row.peakViewers(),
                average == null ? null : Math.round(average * 10.0d) / 10.0d,
                row.viewerSamples(),
                row.vodId());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.streamsense.analyticsservice.persistence;

import com.streamsense.analyticsservice.model.StreamSessionRow;
import com.streamsense.analyticsservice.model.ViewerSample;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class StreamSessionRepository {

    private static final String COLUMNS =
            """
            id, streamer, source, session_ref, twitch_stream_id, stream_session_id, channel_login, title, category,
            started_at, ended_at, last_seen_at, peak_viewers, viewer_sum, viewer_samples
            """;

    private final JdbcTemplate jdbcTemplate;

    public StreamSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(
            String streamer,
            String source,
            String sessionRef,
            String twitchStreamId,
            String streamSessionId,
            String channelLogin,
            String title,
            String category,
            long startedAt,
            long lastSeenAt,
            long now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                            insert into stream_sessions
                                (streamer, source, session_ref, twitch_stream_id, stream_session_id, channel_login,
                                 title, category, started_at, ended_at, last_seen_at, peak_viewers, viewer_sum,
                                 viewer_samples, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?, null, 0, 0, ?, ?)
                            """,
                            Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, streamer);
                    statement.setString(2, source);
                    statement.setString(3, sessionRef);
                    statement.setString(4, twitchStreamId);
                    statement.setString(5, streamSessionId);
                    statement.setString(6, channelLogin);
                    statement.setString(7, title);
                    statement.setString(8, category);
                    statement.setLong(9, startedAt);
                    statement.setLong(10, lastSeenAt);
                    statement.setLong(11, now);
                    statement.setLong(12, now);
                    return statement;
                },
                keys);
        Object id = keys.getKeys() == null ? keys.getKey() : keys.getKeys().get("id");
        return ((Number) Objects.requireNonNull(id, "generated session id")).longValue();
    }

    public Optional<StreamSessionRow> find(String streamer, String source, String sessionRef) {
        List<StreamSessionRow> rows = jdbcTemplate.query(
                "select " + COLUMNS + " from stream_sessions where streamer = ? and source = ? and session_ref = ?",
                this::mapRow,
                streamer,
                source,
                sessionRef);
        return rows.stream().findFirst();
    }

    public Optional<StreamSessionRow> findById(long id) {
        return jdbcTemplate.query("select " + COLUMNS + " from stream_sessions where id = ?", this::mapRow, id).stream()
                .findFirst();
    }

    /** Sessions of a streamer that overlap [from, to), newest first. Null bounds mean unbounded. */
    public List<StreamSessionRow> findByStreamer(String streamer, Long from, Long to, int limit) {
        long lower = from == null ? Long.MIN_VALUE : from;
        long upper = to == null ? Long.MAX_VALUE : to;
        return jdbcTemplate.query(
                "select " + COLUMNS
                        + """
                         from stream_sessions
                        where streamer = ?
                          and started_at < ?
                          and (ended_at is null or ended_at >= ?)
                        order by started_at desc
                        limit ?
                        """,
                this::mapRow,
                streamer,
                upper,
                lower,
                limit);
    }

    public List<StreamSessionRow> findOpen(String source) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from stream_sessions where source = ? and ended_at is null",
                this::mapRow,
                source);
    }

    /** Streamers with any processed event since {@code since}: the channels worth polling. */
    public List<String> findStreamersSeenSince(long since) {
        return jdbcTemplate.queryForList(
                "select distinct streamer from analytics_processed_events where processed_at >= ?",
                String.class,
                since);
    }

    public void touch(long id, long startedAt, long lastSeenAt, long now) {
        jdbcTemplate.update(
                """
                update stream_sessions
                set started_at = ?, last_seen_at = ?, ended_at = null, updated_at = ?
                where id = ?
                """,
                startedAt,
                lastSeenAt,
                now,
                id);
    }

    public void recordLive(long id, String title, String category, int viewerCount, long sampledAt, long now) {
        jdbcTemplate.update(
                """
                update stream_sessions
                set title = ?,
                    category = ?,
                    last_seen_at = ?,
                    ended_at = null,
                    peak_viewers = case when peak_viewers is null or peak_viewers < ? then ? else peak_viewers end,
                    viewer_sum = viewer_sum + ?,
                    viewer_samples = viewer_samples + 1,
                    updated_at = ?
                where id = ?
                """,
                title,
                category,
                sampledAt,
                viewerCount,
                viewerCount,
                viewerCount,
                now,
                id);
        try {
            jdbcTemplate.update(
                    "insert into stream_session_viewers (session_id, sampled_at, viewer_count) values (?, ?, ?)",
                    id,
                    sampledAt,
                    viewerCount);
        } catch (DuplicateKeyException ex) {
            // Two polls in the same millisecond; the first sample stands.
        }
    }

    public void close(long id, long endedAt, long now) {
        jdbcTemplate.update(
                "update stream_sessions set ended_at = ?, updated_at = ? where id = ? and ended_at is null",
                endedAt,
                now,
                id);
    }

    /** Closes open sessions of a source that have not been seen since {@code idleBefore}, at their last event. */
    public int closeIdle(String source, long idleBefore, long now) {
        return jdbcTemplate.update(
                """
                update stream_sessions
                set ended_at = last_seen_at, updated_at = ?
                where source = ? and ended_at is null and last_seen_at < ?
                """,
                now,
                source,
                idleBefore);
    }

    public List<ViewerSample> findViewerSamples(long sessionId) {
        return jdbcTemplate.query(
                "select sampled_at, viewer_count from stream_session_viewers where session_id = ? order by sampled_at",
                (rs, rowNum) -> new ViewerSample(rs.getLong("sampled_at"), rs.getInt("viewer_count")),
                sessionId);
    }

    private StreamSessionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StreamSessionRow(
                rs.getLong("id"),
                rs.getString("streamer"),
                rs.getString("source"),
                rs.getString("session_ref"),
                rs.getString("twitch_stream_id"),
                rs.getString("stream_session_id"),
                rs.getString("channel_login"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getLong("started_at"),
                rs.getObject("ended_at", Long.class),
                rs.getLong("last_seen_at"),
                rs.getObject("peak_viewers", Integer.class),
                rs.getLong("viewer_sum"),
                rs.getInt("viewer_samples"));
    }
}

package com.streamsense.analyticsservice.persistence;

import com.streamsense.analyticsservice.model.VodImportRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VodImportRepository {

    private static final String COLUMNS =
            """
            streamer, vod_id, session_id, state, offset_seconds, duration_seconds, chat_state, chat_offset_seconds,
            capture_state, capture_offset_seconds, last_error, requested_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public VodImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts the row or replaces every column of the existing one; the key is the streamer and the recording. */
    public void save(VodImportRow row) {
        int updated = jdbcTemplate.update(
                """
                update vod_imports
                set session_id = ?, state = ?, offset_seconds = ?, duration_seconds = ?, chat_state = ?,
                    chat_offset_seconds = ?, capture_state = ?, capture_offset_seconds = ?, last_error = ?,
                    requested_at = ?, updated_at = ?
                where streamer = ? and vod_id = ?
                """,
                row.sessionId(),
                row.state(),
                row.offsetSeconds(),
                row.durationSeconds(),
                row.chatState(),
                row.chatOffsetSeconds(),
                row.captureState(),
                row.captureOffsetSeconds(),
                row.lastError(),
                row.requestedAt(),
                row.updatedAt(),
                row.streamer(),
                row.vodId());
        if (updated == 0) {
            jdbcTemplate.update(
                    "insert into vod_imports (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    row.streamer(),
                    row.vodId(),
                    row.sessionId(),
                    row.state(),
                    row.offsetSeconds(),
                    row.durationSeconds(),
                    row.chatState(),
                    row.chatOffsetSeconds(),
                    row.captureState(),
                    row.captureOffsetSeconds(),
                    row.lastError(),
                    row.requestedAt(),
                    row.updatedAt());
        }
    }

    public Optional<VodImportRow> find(String streamer, String vodId) {
        return jdbcTemplate
                .query(
                        "select " + COLUMNS + " from vod_imports where streamer = ? and vod_id = ?",
                        this::mapRow,
                        streamer,
                        vodId)
                .stream()
                .findFirst();
    }

    /** Every import the channel has asked for, the most recently requested first. */
    public List<VodImportRow> findByStreamer(String streamer) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from vod_imports where streamer = ? order by requested_at desc",
                this::mapRow,
                streamer);
    }

    /** The imports a service may still be working on, or that were asked to stop and have not confirmed. */
    public List<VodImportRow> findActive() {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from vod_imports where state in ('QUEUED', 'IMPORTING', 'STOPPING')",
                this::mapRow);
    }

    private VodImportRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        long sessionId = rs.getLong("session_id");
        return new VodImportRow(
                rs.getString("streamer"),
                rs.getString("vod_id"),
                rs.wasNull() ? null : sessionId,
                rs.getString("state"),
                rs.getLong("offset_seconds"),
                rs.getLong("duration_seconds"),
                rs.getString("chat_state"),
                rs.getLong("chat_offset_seconds"),
                rs.getString("capture_state"),
                rs.getLong("capture_offset_seconds"),
                rs.getString("last_error"),
                rs.getLong("requested_at"),
                rs.getLong("updated_at"));
    }
}

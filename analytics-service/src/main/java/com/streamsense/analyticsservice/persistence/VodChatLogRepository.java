package com.streamsense.analyticsservice.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.analyticsservice.model.ChatLogLine;
import com.streamsense.analyticsservice.model.VodChatLogRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VodChatLogRepository {

    private static final String SUMMARY_COLUMNS =
            "streamer, vod_id, file_name, line_count, first_offset_seconds, last_offset_seconds, uploaded_at";
    private static final TypeReference<List<ChatLogLine>> LINES = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public VodChatLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** Keeps the log for the recording, replacing any earlier one. */
    public VodChatLogRow save(String streamer, String vodId, String fileName, List<ChatLogLine> lines, long now) {
        String content;
        try {
            content = objectMapper.writeValueAsString(lines);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("chat log could not be serialised", exception);
        }
        double first = lines.isEmpty() ? 0 : lines.get(0).offsetSeconds();
        double last = lines.isEmpty() ? 0 : lines.get(lines.size() - 1).offsetSeconds();
        int updated = jdbcTemplate.update(
                """
                update vod_chat_logs
                set file_name = ?, line_count = ?, first_offset_seconds = ?, last_offset_seconds = ?, content = ?,
                    uploaded_at = ?
                where streamer = ? and vod_id = ?
                """,
                fileName,
                lines.size(),
                first,
                last,
                content,
                now,
                streamer,
                vodId);
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    insert into vod_chat_logs
                        (streamer, vod_id, file_name, line_count, first_offset_seconds, last_offset_seconds, content,
                         uploaded_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    streamer,
                    vodId,
                    fileName,
                    lines.size(),
                    first,
                    last,
                    content,
                    now);
        }
        return new VodChatLogRow(streamer, vodId, fileName, lines.size(), first, last, now);
    }

    public Optional<VodChatLogRow> find(String streamer, String vodId) {
        return jdbcTemplate
                .query(
                        "select " + SUMMARY_COLUMNS + " from vod_chat_logs where streamer = ? and vod_id = ?",
                        this::mapRow,
                        streamer,
                        vodId)
                .stream()
                .findFirst();
    }

    /** The recordings of the channel that have a log, keyed by recording id. */
    public List<VodChatLogRow> findByStreamer(String streamer) {
        return jdbcTemplate.query(
                "select " + SUMMARY_COLUMNS + " from vod_chat_logs where streamer = ?", this::mapRow, streamer);
    }

    /** The log's lines in playback order; empty when the recording has none. */
    public List<ChatLogLine> lines(String streamer, String vodId) {
        List<String> contents = jdbcTemplate.query(
                "select content from vod_chat_logs where streamer = ? and vod_id = ?",
                (rs, rowNum) -> rs.getString("content"),
                streamer,
                vodId);
        if (contents.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(contents.get(0), LINES);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored chat log could not be read", exception);
        }
    }

    private VodChatLogRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new VodChatLogRow(
                rs.getString("streamer"),
                rs.getString("vod_id"),
                rs.getString("file_name"),
                rs.getInt("line_count"),
                rs.getDouble("first_offset_seconds"),
                rs.getDouble("last_offset_seconds"),
                rs.getLong("uploaded_at"));
    }
}

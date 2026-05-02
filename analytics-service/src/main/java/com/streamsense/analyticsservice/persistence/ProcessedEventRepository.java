package com.streamsense.analyticsservice.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public ProcessedEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.postgres = isPostgres(jdbcTemplate);
    }

    public boolean tryMarkProcessed(String topic, String eventId, String streamer, String streamSessionId,
            long eventTimestamp, long processedAt) {
        if (postgres) {
            int inserted = jdbcTemplate.update("""
                    insert into analytics_processed_events
                        (source_topic, source_event_id, streamer, stream_session_id, event_timestamp, processed_at)
                    values (?, ?, ?, ?, ?, ?)
                    on conflict (source_topic, source_event_id) do nothing
                    """, topic, eventId, streamer, streamSessionId, eventTimestamp, processedAt);
            return inserted == 1;
        }
        try {
            jdbcTemplate.update("""
                    insert into analytics_processed_events
                        (source_topic, source_event_id, streamer, stream_session_id, event_timestamp, processed_at)
                    values (?, ?, ?, ?, ?, ?)
                    """, topic, eventId, streamer, streamSessionId, eventTimestamp, processedAt);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private boolean isPostgres(JdbcTemplate jdbcTemplate) {
        try {
            Boolean result = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> connection.getMetaData()
                    .getDatabaseProductName().toLowerCase().contains("postgresql"));
            return Boolean.TRUE.equals(result);
        } catch (DataAccessException ex) {
            return false;
        }
    }
}

package com.streamsense.analyticsservice.persistence;

import com.streamsense.analyticsservice.model.StreamBucketMetric;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MetricBucketRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public MetricBucketRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.postgres = isPostgres(jdbcTemplate);
    }

    public void incrementChatMessage(
            String streamer,
            String channelLogin,
            String streamSessionId,
            String sessionKey,
            String twitchStreamId,
            long bucketStart,
            int bucketSizeSeconds,
            long now) {
        ensureBucket(
                streamer,
                channelLogin,
                streamSessionId,
                sessionKey,
                twitchStreamId,
                bucketStart,
                bucketSizeSeconds,
                now);
        jdbcTemplate.update(
                """
                update stream_metric_buckets
                set chat_message_count = chat_message_count + 1,
                    updated_at = ?
                where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ?
                """,
                now,
                streamer,
                sessionKey,
                bucketStart,
                bucketSizeSeconds);
    }

    public void incrementChatSentiment(
            String streamer,
            String channelLogin,
            String streamSessionId,
            String sessionKey,
            String twitchStreamId,
            long bucketStart,
            int bucketSizeSeconds,
            String label,
            double score,
            long now) {
        ensureBucket(
                streamer,
                channelLogin,
                streamSessionId,
                sessionKey,
                twitchStreamId,
                bucketStart,
                bucketSizeSeconds,
                now);
        LabelCounts counts = LabelCounts.from(label);
        jdbcTemplate.update(
                """
                update stream_metric_buckets
                set chat_sentiment_count = chat_sentiment_count + 1,
                    chat_positive_count = chat_positive_count + ?,
                    chat_neutral_count = chat_neutral_count + ?,
                    chat_negative_count = chat_negative_count + ?,
                    chat_score_sum = chat_score_sum + ?,
                    updated_at = ?
                where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ?
                """,
                counts.positive(),
                counts.neutral(),
                counts.negative(),
                score,
                now,
                streamer,
                sessionKey,
                bucketStart,
                bucketSizeSeconds);
    }

    public void incrementTranscriptSentiment(
            String streamer,
            String streamSessionId,
            String sessionKey,
            long bucketStart,
            int bucketSizeSeconds,
            String label,
            double score,
            long now) {
        ensureBucket(streamer, null, streamSessionId, sessionKey, null, bucketStart, bucketSizeSeconds, now);
        LabelCounts counts = LabelCounts.from(label);
        jdbcTemplate.update(
                """
                update stream_metric_buckets
                set transcript_sentiment_count = transcript_sentiment_count + 1,
                    transcript_positive_count = transcript_positive_count + ?,
                    transcript_neutral_count = transcript_neutral_count + ?,
                    transcript_negative_count = transcript_negative_count + ?,
                    transcript_score_sum = transcript_score_sum + ?,
                    updated_at = ?
                where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ?
                """,
                counts.positive(),
                counts.neutral(),
                counts.negative(),
                score,
                now,
                streamer,
                sessionKey,
                bucketStart,
                bucketSizeSeconds);
    }

    public void setSpikeFlags(
            String streamer,
            String sessionKey,
            long bucketStart,
            int bucketSizeSeconds,
            boolean negativeSpike,
            boolean engagementSpike,
            long now) {
        jdbcTemplate.update(
                """
                update stream_metric_buckets
                set negative_spike_count = ?,
                    engagement_spike_count = ?,
                    updated_at = ?
                where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ?
                """,
                negativeSpike ? 1 : 0,
                engagementSpike ? 1 : 0,
                now,
                streamer,
                sessionKey,
                bucketStart,
                bucketSizeSeconds);
    }

    public void insertChatter(
            String streamer,
            String sessionKey,
            long bucketStart,
            int bucketSizeSeconds,
            String username,
            long firstSeenAt) {
        if (username == null || username.isBlank()) {
            return;
        }
        if (postgres) {
            jdbcTemplate.update(
                    """
                    insert into stream_bucket_chatters
                        (streamer, session_key, bucket_start, bucket_size_seconds, username, first_seen_at)
                    values (?, ?, ?, ?, ?, ?)
                    on conflict (streamer, session_key, bucket_start, bucket_size_seconds, username) do nothing
                    """,
                    streamer,
                    sessionKey,
                    bucketStart,
                    bucketSizeSeconds,
                    username.trim().toLowerCase(),
                    firstSeenAt);
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    insert into stream_bucket_chatters
                        (streamer, session_key, bucket_start, bucket_size_seconds, username, first_seen_at)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    streamer,
                    sessionKey,
                    bucketStart,
                    bucketSizeSeconds,
                    username.trim().toLowerCase(),
                    firstSeenAt);
        } catch (DuplicateKeyException ex) {
            // Same chatter can post multiple messages in the same bucket.
        }
    }

    public List<StreamBucketMetric> findBuckets(
            String streamer, String sessionKey, long windowStart, long windowEnd, int bucketSizeSeconds) {
        String sessionClause = sessionKey == null ? "" : " and b.session_key = ?";
        Object[] args = sessionKey == null
                ? new Object[] {streamer, bucketSizeSeconds, windowStart, windowEnd}
                : new Object[] {streamer, bucketSizeSeconds, windowStart, windowEnd, sessionKey};
        return jdbcTemplate.query(
                """
                select b.*,
                       (select count(*) from stream_bucket_chatters c
                        where c.streamer = b.streamer
                          and c.session_key = b.session_key
                          and c.bucket_start = b.bucket_start
                          and c.bucket_size_seconds = b.bucket_size_seconds) as unique_chatters
                from stream_metric_buckets b
                where b.streamer = ?
                  and b.bucket_size_seconds = ?
                  and b.bucket_start >= ?
                  and b.bucket_start < ?
                """
                        + sessionClause + " order by b.bucket_start asc",
                args,
                this::mapBucket);
    }

    public Long latestEventAt(String streamer, String sessionKey) {
        String sessionClause = sessionKey == null ? "" : " and stream_session_id = ?";
        Object[] args = sessionKey == null ? new Object[] {streamer} : new Object[] {streamer, sessionKey};
        return jdbcTemplate.query(
                "select max(event_timestamp) from analytics_processed_events where streamer = ?" + sessionClause,
                args,
                rs -> rs.next() ? rs.getObject(1, Long.class) : null);
    }

    public long countUniqueChatters(
            String streamer, String sessionKey, long windowStart, long windowEnd, int bucketSizeSeconds) {
        String sessionClause = sessionKey == null ? "" : " and session_key = ?";
        Object[] args = sessionKey == null
                ? new Object[] {streamer, bucketSizeSeconds, windowStart, windowEnd}
                : new Object[] {streamer, bucketSizeSeconds, windowStart, windowEnd, sessionKey};
        Long count = jdbcTemplate.queryForObject(
                """
                select count(distinct username)
                from stream_bucket_chatters
                where streamer = ?
                  and bucket_size_seconds = ?
                  and bucket_start >= ?
                  and bucket_start < ?
                """
                        + sessionClause,
                Long.class,
                args);
        return count == null ? 0 : count;
    }

    private void ensureBucket(
            String streamer,
            String channelLogin,
            String streamSessionId,
            String sessionKey,
            String twitchStreamId,
            long bucketStart,
            int bucketSizeSeconds,
            long now) {
        int inserted = insertBucket(
                streamer,
                channelLogin,
                streamSessionId,
                sessionKey,
                twitchStreamId,
                bucketStart,
                bucketSizeSeconds,
                now);
        if (inserted == 0 && (channelLogin != null || streamSessionId != null || twitchStreamId != null)) {
            jdbcTemplate.update(
                    """
                    update stream_metric_buckets
                    set channel_login = coalesce(channel_login, ?),
                        stream_session_id = coalesce(stream_session_id, ?),
                        twitch_stream_id = coalesce(twitch_stream_id, ?)
                    where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ?
                    """,
                    channelLogin,
                    streamSessionId,
                    twitchStreamId,
                    streamer,
                    sessionKey,
                    bucketStart,
                    bucketSizeSeconds);
        }
    }

    private int insertBucket(
            String streamer,
            String channelLogin,
            String streamSessionId,
            String sessionKey,
            String twitchStreamId,
            long bucketStart,
            int bucketSizeSeconds,
            long now) {
        if (postgres) {
            return jdbcTemplate.update(
                    """
                    insert into stream_metric_buckets
                        (streamer, channel_login, stream_session_id, session_key, twitch_stream_id, bucket_start,
                         bucket_size_seconds, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (streamer, session_key, bucket_start, bucket_size_seconds) do nothing
                    """,
                    streamer,
                    channelLogin,
                    streamSessionId,
                    sessionKey,
                    twitchStreamId,
                    bucketStart,
                    bucketSizeSeconds,
                    now,
                    now);
        }
        try {
            jdbcTemplate.update(
                    """
                    insert into stream_metric_buckets
                        (streamer, channel_login, stream_session_id, session_key, twitch_stream_id, bucket_start,
                         bucket_size_seconds, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    streamer,
                    channelLogin,
                    streamSessionId,
                    sessionKey,
                    twitchStreamId,
                    bucketStart,
                    bucketSizeSeconds,
                    now,
                    now);
            return 1;
        } catch (DuplicateKeyException ex) {
            return 0;
        }
    }

    private boolean isPostgres(JdbcTemplate jdbcTemplate) {
        try {
            Boolean result = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> connection
                    .getMetaData()
                    .getDatabaseProductName()
                    .toLowerCase()
                    .contains("postgresql"));
            return Boolean.TRUE.equals(result);
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private StreamBucketMetric mapBucket(ResultSet rs, int rowNum) throws SQLException {
        return new StreamBucketMetric(
                rs.getLong("bucket_start"),
                rs.getInt("bucket_size_seconds"),
                rs.getLong("chat_message_count"),
                rs.getLong("unique_chatters"),
                rs.getLong("chat_sentiment_count"),
                rs.getLong("chat_positive_count"),
                rs.getLong("chat_neutral_count"),
                rs.getLong("chat_negative_count"),
                rs.getDouble("chat_score_sum"),
                rs.getLong("transcript_sentiment_count"),
                rs.getLong("transcript_positive_count"),
                rs.getLong("transcript_neutral_count"),
                rs.getLong("transcript_negative_count"),
                rs.getDouble("transcript_score_sum"),
                rs.getLong("negative_spike_count"),
                rs.getLong("engagement_spike_count"));
    }

    private record LabelCounts(int positive, int neutral, int negative) {
        static LabelCounts from(String label) {
            if ("POSITIVE".equalsIgnoreCase(label)) {
                return new LabelCounts(1, 0, 0);
            }
            if ("NEGATIVE".equalsIgnoreCase(label)) {
                return new LabelCounts(0, 0, 1);
            }
            return new LabelCounts(0, 1, 0);
        }
    }
}

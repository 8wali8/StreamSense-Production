package com.streamsense.analyticsservice.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.streamsense.analyticsservice.model.SponsorBucketMetric;
import com.streamsense.analyticsservice.model.SponsorBucketTotals;

@Repository
public class SponsorMetricBucketRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public SponsorMetricBucketRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.postgres = isPostgres(jdbcTemplate);
    }

    public void incrementSponsor(String streamer, String channelLogin, String streamSessionId, String sessionKey,
            String twitchStreamId, long bucketStart, int bucketSizeSeconds, String sponsor, double confidence,
            boolean accepted, boolean fallback, long exposureMs, long now) {
        String normalizedSponsor = normalizeSponsor(sponsor);
        ensureBucket(streamer, channelLogin, streamSessionId, sessionKey, twitchStreamId, bucketStart, bucketSizeSeconds,
                normalizedSponsor, now);
        jdbcTemplate.update("""
                update sponsor_metric_buckets
                set detection_count = detection_count + 1,
                    accepted_detection_count = accepted_detection_count + ?,
                    low_confidence_detection_count = low_confidence_detection_count + ?,
                    fallback_detection_count = fallback_detection_count + ?,
                    estimated_exposure_ms = estimated_exposure_ms + ?,
                    confidence_sum = confidence_sum + ?,
                    max_confidence = case when max_confidence is null or max_confidence < ? then ? else max_confidence end,
                    updated_at = ?
                where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ? and sponsor = ?
                """, accepted ? 1 : 0, accepted ? 0 : 1, fallback ? 1 : 0, accepted ? exposureMs : 0,
                confidence, confidence, confidence, now, streamer, sessionKey, bucketStart, bucketSizeSeconds,
                normalizedSponsor);
    }

    public List<SponsorBucketMetric> findSponsorMetrics(String streamer, String sessionKey, long windowStart,
            long windowEnd, int bucketSizeSeconds) {
        String sessionClause = sessionKey == null ? "" : " and session_key = ?";
        Object[] args = sessionKey == null
                ? new Object[] { streamer, bucketSizeSeconds, windowStart, windowEnd }
                : new Object[] { streamer, bucketSizeSeconds, windowStart, windowEnd, sessionKey };
        return jdbcTemplate.query("""
                select sponsor,
                       sum(detection_count) as detection_count,
                       sum(accepted_detection_count) as accepted_detection_count,
                       sum(low_confidence_detection_count) as low_confidence_detection_count,
                       sum(fallback_detection_count) as fallback_detection_count,
                       sum(estimated_exposure_ms) as estimated_exposure_ms,
                       sum(confidence_sum) as confidence_sum,
                       max(max_confidence) as max_confidence
                from sponsor_metric_buckets
                where streamer = ?
                  and bucket_size_seconds = ?
                  and bucket_start >= ?
                  and bucket_start < ?
                """ + sessionClause + " group by sponsor order by accepted_detection_count desc, detection_count desc, sponsor asc",
                args, this::mapSponsor);
    }

    public List<SponsorBucketTotals> findSponsorTotalsByBucket(String streamer, String sessionKey, long windowStart,
            long windowEnd, int bucketSizeSeconds) {
        String sessionClause = sessionKey == null ? "" : " and session_key = ?";
        Object[] args = sessionKey == null
                ? new Object[] { streamer, bucketSizeSeconds, windowStart, windowEnd }
                : new Object[] { streamer, bucketSizeSeconds, windowStart, windowEnd, sessionKey };
        return jdbcTemplate.query("""
                select bucket_start,
                       sum(detection_count) as detection_count,
                       sum(estimated_exposure_ms) as estimated_exposure_ms
                from sponsor_metric_buckets
                where streamer = ?
                  and bucket_size_seconds = ?
                  and bucket_start >= ?
                  and bucket_start < ?
                """ + sessionClause + " group by bucket_start order by bucket_start asc", this::mapTotals, args);
    }

    private void ensureBucket(String streamer, String channelLogin, String streamSessionId, String sessionKey,
            String twitchStreamId, long bucketStart, int bucketSizeSeconds, String sponsor, long now) {
        int inserted = insertBucket(streamer, channelLogin, streamSessionId, sessionKey, twitchStreamId, bucketStart,
                bucketSizeSeconds, sponsor, now);
        if (inserted == 0 && (channelLogin != null || streamSessionId != null || twitchStreamId != null)) {
            jdbcTemplate.update("""
                    update sponsor_metric_buckets
                    set channel_login = coalesce(channel_login, ?),
                        stream_session_id = coalesce(stream_session_id, ?),
                        twitch_stream_id = coalesce(twitch_stream_id, ?)
                    where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ? and sponsor = ?
                    """, channelLogin, streamSessionId, twitchStreamId, streamer, sessionKey, bucketStart,
                    bucketSizeSeconds, sponsor);
        }
    }

    private int insertBucket(String streamer, String channelLogin, String streamSessionId, String sessionKey,
            String twitchStreamId, long bucketStart, int bucketSizeSeconds, String sponsor, long now) {
        if (postgres) {
            return jdbcTemplate.update("""
                    insert into sponsor_metric_buckets
                        (streamer, channel_login, stream_session_id, session_key, twitch_stream_id, bucket_start,
                         bucket_size_seconds, sponsor, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (streamer, session_key, bucket_start, bucket_size_seconds, sponsor) do nothing
                    """, streamer, channelLogin, streamSessionId, sessionKey, twitchStreamId, bucketStart,
                    bucketSizeSeconds, sponsor, now, now);
        }
        try {
            jdbcTemplate.update("""
                    insert into sponsor_metric_buckets
                        (streamer, channel_login, stream_session_id, session_key, twitch_stream_id, bucket_start,
                         bucket_size_seconds, sponsor, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, streamer, channelLogin, streamSessionId, sessionKey, twitchStreamId, bucketStart,
                    bucketSizeSeconds, sponsor, now, now);
            return 1;
        } catch (DuplicateKeyException ex) {
            return 0;
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

    private SponsorBucketMetric mapSponsor(ResultSet rs, int rowNum) throws SQLException {
        return new SponsorBucketMetric(
                rs.getString("sponsor"),
                rs.getLong("detection_count"),
                rs.getLong("accepted_detection_count"),
                rs.getLong("low_confidence_detection_count"),
                rs.getLong("fallback_detection_count"),
                rs.getLong("estimated_exposure_ms"),
                rs.getDouble("confidence_sum"),
                rs.getObject("max_confidence", Double.class));
    }

    private SponsorBucketTotals mapTotals(ResultSet rs, int rowNum) throws SQLException {
        return new SponsorBucketTotals(
                rs.getLong("bucket_start"),
                rs.getLong("detection_count"),
                rs.getLong("estimated_exposure_ms"));
    }

    private String normalizeSponsor(String sponsor) {
        if (sponsor == null || sponsor.isBlank()) {
            return "UNKNOWN";
        }
        return sponsor.trim();
    }
}

package com.streamsense.analyticsservice.persistence;

import com.streamsense.analyticsservice.model.SponsorMentionTotals;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sponsor-relevant sentiment counted per sponsor, channel, and bucket. */
@Repository
public class SponsorMentionRepository {

    public static final String CHANNEL_CHAT = "CHAT";
    public static final String CHANNEL_VOICE = "VOICE";

    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public SponsorMentionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.postgres = Dialect.isPostgres(jdbcTemplate);
    }

    public void increment(
            String streamer,
            String sessionKey,
            long bucketStart,
            int bucketSizeSeconds,
            String sponsor,
            String channel,
            String label,
            double score,
            long now) {
        String normalizedSponsor = sponsor.trim();
        String insert =
                """
                insert into sponsor_mention_buckets
                    (streamer, session_key, bucket_start, bucket_size_seconds, sponsor, channel, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        if (postgres) {
            jdbcTemplate.update(
                    insert + " on conflict (streamer, session_key, bucket_start, bucket_size_seconds, sponsor, channel)"
                            + " do nothing",
                    streamer,
                    sessionKey,
                    bucketStart,
                    bucketSizeSeconds,
                    normalizedSponsor,
                    channel,
                    now,
                    now);
        } else {
            try {
                jdbcTemplate.update(
                        insert,
                        streamer,
                        sessionKey,
                        bucketStart,
                        bucketSizeSeconds,
                        normalizedSponsor,
                        channel,
                        now,
                        now);
            } catch (DuplicateKeyException ex) {
                // Bucket exists; fall through to the increment.
            }
        }
        String upper = label == null ? "" : label.toUpperCase(Locale.ROOT);
        int positive = "POSITIVE".equals(upper) ? 1 : 0;
        int negative = "NEGATIVE".equals(upper) ? 1 : 0;
        int neutral = positive == 0 && negative == 0 ? 1 : 0;
        jdbcTemplate.update(
                """
                update sponsor_mention_buckets
                set mention_count = mention_count + 1,
                    positive_count = positive_count + ?,
                    neutral_count = neutral_count + ?,
                    negative_count = negative_count + ?,
                    score_sum = score_sum + ?,
                    updated_at = ?
                where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ?
                  and sponsor = ? and channel = ?
                """,
                positive,
                neutral,
                negative,
                score,
                now,
                streamer,
                sessionKey,
                bucketStart,
                bucketSizeSeconds,
                normalizedSponsor,
                channel);
    }

    /** Totals per channel for one sponsor (case-insensitive) over [windowStart, windowEnd). */
    public List<SponsorMentionTotals> findTotals(
            String streamer,
            String sessionKey,
            long windowStart,
            long windowEnd,
            int bucketSizeSeconds,
            String sponsor) {
        String sessionClause = sessionKey == null ? "" : " and session_key = ?";
        Object[] args = sessionKey == null
                ? new Object[] {streamer, bucketSizeSeconds, windowStart, windowEnd, sponsor.toLowerCase(Locale.ROOT)}
                : new Object[] {
                    streamer, bucketSizeSeconds, windowStart, windowEnd, sponsor.toLowerCase(Locale.ROOT), sessionKey
                };
        return jdbcTemplate.query(
                """
                select channel,
                       sum(mention_count) as mention_count,
                       sum(positive_count) as positive_count,
                       sum(neutral_count) as neutral_count,
                       sum(negative_count) as negative_count,
                       sum(score_sum) as score_sum
                from sponsor_mention_buckets
                where streamer = ?
                  and bucket_size_seconds = ?
                  and bucket_start >= ?
                  and bucket_start < ?
                  and lower(sponsor) = ?
                """
                        + sessionClause + " group by channel",
                (rs, rowNum) -> new SponsorMentionTotals(
                        rs.getString("channel"),
                        rs.getLong("mention_count"),
                        rs.getLong("positive_count"),
                        rs.getLong("neutral_count"),
                        rs.getLong("negative_count"),
                        rs.getDouble("score_sum")),
                args);
    }
}

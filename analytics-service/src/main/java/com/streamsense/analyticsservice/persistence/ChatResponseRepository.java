package com.streamsense.analyticsservice.persistence;

import com.streamsense.analyticsservice.model.CommandTotals;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** What viewers did in chat that a deal can count: commands used and link hosts posted. */
@Repository
public class ChatResponseRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatResponseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void incrementCommand(
            String streamer,
            String sessionKey,
            long bucketStart,
            int bucketSizeSeconds,
            String command,
            String username,
            long at,
            long now) {
        try {
            jdbcTemplate.update(
                    """
                    insert into chat_command_buckets
                        (streamer, session_key, bucket_start, bucket_size_seconds, command, use_count, created_at, updated_at)
                    values (?, ?, ?, ?, ?, 0, ?, ?)
                    """,
                    streamer,
                    sessionKey,
                    bucketStart,
                    bucketSizeSeconds,
                    command,
                    now,
                    now);
        } catch (DuplicateKeyException ex) {
            // Bucket exists.
        }
        jdbcTemplate.update(
                """
                update chat_command_buckets set use_count = use_count + 1, updated_at = ?
                where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ? and command = ?
                """,
                now,
                streamer,
                sessionKey,
                bucketStart,
                bucketSizeSeconds,
                command);
        if (username != null && !username.isBlank()) {
            try {
                jdbcTemplate.update(
                        """
                        insert into chat_command_users (streamer, session_key, command, username, first_seen_at)
                        values (?, ?, ?, ?, ?)
                        """,
                        streamer,
                        sessionKey,
                        command,
                        username.trim().toLowerCase(Locale.ROOT),
                        at);
            } catch (DuplicateKeyException ex) {
                // Same person using the command again.
            }
        }
    }

    public void incrementLink(
            String streamer, String sessionKey, long bucketStart, int bucketSizeSeconds, String host, long now) {
        try {
            jdbcTemplate.update(
                    """
                    insert into chat_link_buckets
                        (streamer, session_key, bucket_start, bucket_size_seconds, host, post_count, created_at, updated_at)
                    values (?, ?, ?, ?, ?, 0, ?, ?)
                    """,
                    streamer,
                    sessionKey,
                    bucketStart,
                    bucketSizeSeconds,
                    host,
                    now,
                    now);
        } catch (DuplicateKeyException ex) {
            // Bucket exists.
        }
        jdbcTemplate.update(
                """
                update chat_link_buckets set post_count = post_count + 1, updated_at = ?
                where streamer = ? and session_key = ? and bucket_start = ? and bucket_size_seconds = ? and host = ?
                """,
                now,
                streamer,
                sessionKey,
                bucketStart,
                bucketSizeSeconds,
                host);
    }

    public CommandTotals commandTotals(
            String streamer,
            String sessionKey,
            long windowStart,
            long windowEnd,
            int bucketSizeSeconds,
            String command) {
        String sessionClause = sessionKey == null ? "" : " and session_key = ?";
        Object[] args = sessionKey == null
                ? new Object[] {streamer, bucketSizeSeconds, windowStart, windowEnd, command}
                : new Object[] {streamer, bucketSizeSeconds, windowStart, windowEnd, command, sessionKey};
        Long uses = jdbcTemplate.queryForObject(
                """
                select coalesce(sum(use_count), 0) from chat_command_buckets
                where streamer = ? and bucket_size_seconds = ? and bucket_start >= ? and bucket_start < ? and command = ?
                """
                        + sessionClause,
                Long.class,
                args);
        Object[] userArgs = sessionKey == null
                ? new Object[] {streamer, command, windowStart, windowEnd}
                : new Object[] {streamer, command, windowStart, windowEnd, sessionKey};
        Long users = jdbcTemplate.queryForObject(
                """
                select count(*) from chat_command_users
                where streamer = ? and command = ? and first_seen_at >= ? and first_seen_at < ?
                """
                        + sessionClause,
                Long.class,
                userArgs);
        return new CommandTotals(uses == null ? 0 : uses, users == null ? 0 : users);
    }

    public long linkPosts(
            String streamer, String sessionKey, long windowStart, long windowEnd, int bucketSizeSeconds, String host) {
        String sessionClause = sessionKey == null ? "" : " and session_key = ?";
        Object[] args = sessionKey == null
                ? new Object[] {streamer, bucketSizeSeconds, windowStart, windowEnd, host}
                : new Object[] {streamer, bucketSizeSeconds, windowStart, windowEnd, host, sessionKey};
        Long posts = jdbcTemplate.queryForObject(
                """
                select coalesce(sum(post_count), 0) from chat_link_buckets
                where streamer = ? and bucket_size_seconds = ? and bucket_start >= ? and bucket_start < ? and host = ?
                """
                        + sessionClause,
                Long.class,
                args);
        return posts == null ? 0 : posts;
    }
}

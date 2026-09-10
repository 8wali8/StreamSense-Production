package com.streamsense.analyticsservice.persistence;

import com.streamsense.analyticsservice.model.DealRow;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DealRepository {

    private static final String COLUMNS =
            """
            id, streamer, sponsor, starts_at, ends_at, promised_streams, fee, currency, cpm_per_30s_equivalent,
            host_read_rate_per_1000, tracked_link, chat_command, channel_point_reward, share_token, created_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public DealRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(DealRow deal, long now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                            insert into deals
                                (streamer, sponsor, starts_at, ends_at, promised_streams, fee, currency,
                                 cpm_per_30s_equivalent, host_read_rate_per_1000, tracked_link, chat_command,
                                 channel_point_reward, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, deal.streamer());
                    statement.setString(2, deal.sponsor());
                    statement.setLong(3, deal.startsAt());
                    statement.setObject(4, deal.endsAt());
                    statement.setObject(5, deal.promisedStreams());
                    statement.setObject(6, deal.fee());
                    statement.setString(7, deal.currency());
                    statement.setDouble(8, deal.cpmPer30sEquivalent());
                    statement.setDouble(9, deal.hostReadRatePer1000());
                    statement.setString(10, deal.trackedLink());
                    statement.setString(11, deal.chatCommand());
                    statement.setString(12, deal.channelPointReward());
                    statement.setLong(13, now);
                    statement.setLong(14, now);
                    return statement;
                },
                keys);
        Object id = keys.getKeys() == null ? keys.getKey() : keys.getKeys().get("id");
        return ((Number) Objects.requireNonNull(id, "generated deal id")).longValue();
    }

    public Optional<DealRow> findById(long id) {
        return jdbcTemplate.query("select " + COLUMNS + " from deals where id = ?", this::mapRow, id).stream()
                .findFirst();
    }

    /** A streamer's deals, newest start first. */
    public List<DealRow> findByStreamer(String streamer, int limit) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from deals where streamer = ? order by starts_at desc, id desc limit ?",
                this::mapRow,
                streamer,
                limit);
    }

    /** Every deal, newest start first. */
    public List<DealRow> findAll(int limit) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from deals order by starts_at desc, id desc limit ?", this::mapRow, limit);
    }

    /** A streamer's deals whose dates cover the instant, newest start first. */
    public List<DealRow> findCovering(String streamer, long at) {
        return jdbcTemplate.query(
                "select " + COLUMNS
                        + """
                         from deals
                        where streamer = ? and starts_at <= ? and (ends_at is null or ends_at > ?)
                        order by starts_at desc, id desc
                        """,
                this::mapRow,
                streamer,
                at,
                at);
    }

    /** Streamers with a deal covering the instant. */
    public List<String> findStreamersWithDealCovering(long at) {
        return jdbcTemplate.queryForList(
                "select distinct streamer from deals where starts_at <= ? and (ends_at is null or ends_at > ?)",
                String.class,
                at,
                at);
    }

    public Optional<DealRow> findByShareToken(String token) {
        return jdbcTemplate
                .query("select " + COLUMNS + " from deals where share_token = ?", this::mapRow, token)
                .stream()
                .findFirst();
    }

    /** Sets or clears (null) the share token. */
    public void updateShareToken(long id, String token, long now) {
        jdbcTemplate.update("update deals set share_token = ?, updated_at = ? where id = ?", token, now, id);
    }

    private DealRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DealRow(
                rs.getLong("id"),
                rs.getString("streamer"),
                rs.getString("sponsor"),
                rs.getLong("starts_at"),
                rs.getObject("ends_at", Long.class),
                rs.getObject("promised_streams", Integer.class),
                rs.getObject("fee", Double.class),
                rs.getString("currency"),
                rs.getDouble("cpm_per_30s_equivalent"),
                rs.getDouble("host_read_rate_per_1000"),
                rs.getString("tracked_link"),
                rs.getString("chat_command"),
                rs.getString("channel_point_reward"),
                rs.getString("share_token"),
                rs.getLong("created_at"));
    }
}

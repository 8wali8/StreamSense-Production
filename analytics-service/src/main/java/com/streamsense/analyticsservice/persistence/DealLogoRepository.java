package com.streamsense.analyticsservice.persistence;

import com.streamsense.analyticsservice.model.DealLogoRow;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DealLogoRepository {

    private static final String COLUMNS =
            "id, deal_id, object_key, content_type, sha256, width, height, size_bytes, uploaded_at";

    private final JdbcTemplate jdbcTemplate;

    public DealLogoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(DealLogoRow logo) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                            insert into deal_logos
                                (deal_id, object_key, content_type, sha256, width, height, size_bytes, uploaded_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            Statement.RETURN_GENERATED_KEYS);
                    statement.setLong(1, logo.dealId());
                    statement.setString(2, logo.objectKey());
                    statement.setString(3, logo.contentType());
                    statement.setString(4, logo.sha256());
                    statement.setInt(5, logo.width());
                    statement.setInt(6, logo.height());
                    statement.setLong(7, logo.sizeBytes());
                    statement.setLong(8, logo.uploadedAt());
                    return statement;
                },
                keys);
        Object id = keys.getKeys() == null ? keys.getKey() : keys.getKeys().get("id");
        return ((Number) Objects.requireNonNull(id, "generated deal logo id")).longValue();
    }

    /** The logo when it belongs to the deal; a logo id alone is never enough to reach one. */
    public Optional<DealLogoRow> findByDealAndId(long dealId, long id) {
        return jdbcTemplate
                .query("select " + COLUMNS + " from deal_logos where deal_id = ? and id = ?", this::mapRow, dealId, id)
                .stream()
                .findFirst();
    }

    /** A deal's logos, oldest upload first. */
    public List<DealLogoRow> findByDeal(long dealId) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from deal_logos where deal_id = ? order by uploaded_at asc, id asc",
                this::mapRow,
                dealId);
    }

    /** The logos of several deals in one query, grouped by deal; a deal without any is absent from the map. */
    public Map<Long, List<DealLogoRow>> findByDeals(Collection<Long> dealIds) {
        if (dealIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", dealIds.stream().map(id -> "?").toList());
        return jdbcTemplate
                .query(
                        "select " + COLUMNS + " from deal_logos where deal_id in (" + placeholders
                                + ") order by uploaded_at asc, id asc",
                        this::mapRow,
                        dealIds.toArray())
                .stream()
                .collect(Collectors.groupingBy(DealLogoRow::dealId));
    }

    public int countByDeal(long dealId) {
        Integer count =
                jdbcTemplate.queryForObject("select count(*) from deal_logos where deal_id = ?", Integer.class, dealId);
        return count == null ? 0 : count;
    }

    public boolean delete(long id) {
        return jdbcTemplate.update("delete from deal_logos where id = ?", id) > 0;
    }

    private DealLogoRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DealLogoRow(
                rs.getLong("id"),
                rs.getLong("deal_id"),
                rs.getString("object_key"),
                rs.getString("content_type"),
                rs.getString("sha256"),
                rs.getInt("width"),
                rs.getInt("height"),
                rs.getLong("size_bytes"),
                rs.getLong("uploaded_at"));
    }
}

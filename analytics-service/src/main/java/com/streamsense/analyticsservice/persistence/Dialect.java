package com.streamsense.analyticsservice.persistence;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Which database a repository talks to. Postgres aborts the whole transaction when an insert hits a
 * unique key, so an "insert, ignore the duplicate, then update" sequence dead-letters every second
 * event in a bucket; there the insert must say {@code on conflict do nothing}. H2 (the tests) has no
 * such clause in every mode but keeps the transaction usable after a caught duplicate.
 */
final class Dialect {

    private Dialect() {}

    static boolean isPostgres(JdbcTemplate jdbcTemplate) {
        try {
            Boolean result = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> connection
                    .getMetaData()
                    .getDatabaseProductName()
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains("postgresql"));
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}

package com.streamsense.sentimentservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

/**
 * One row per sponsor in the catalog, keyed by the lower-cased name. Terms are stored one per line.
 * A removed entry keeps its row with {@code removed} set, which is what stops the configured
 * sponsors from bringing it back at the next start.
 */
@Entity
@Table(name = "sponsor_catalog")
public class SponsorCatalogEntity {

    /** What a term list may hold once joined: the width of its column. */
    public static final int MAX_STORED_TERMS = 4000;

    private static final String TERM_SEPARATOR = "\n";

    @Id
    @Column(name = "sponsor_key", nullable = false, length = 255)
    private String sponsorKey;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "aliases", nullable = false, length = MAX_STORED_TERMS)
    private String aliases = "";

    @Column(name = "semantic_terms", nullable = false, length = MAX_STORED_TERMS)
    private String semanticTerms = "";

    @Column(name = "min_score")
    private Double minScore;

    @Column(name = "removed", nullable = false)
    private boolean removed;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    protected SponsorCatalogEntity() {}

    public SponsorCatalogEntity(
            String sponsorKey,
            String name,
            List<String> aliases,
            List<String> semanticTerms,
            Double minScore,
            long updatedAt) {
        this.sponsorKey = sponsorKey;
        this.name = name;
        this.aliases = join(aliases);
        this.semanticTerms = join(semanticTerms);
        this.minScore = minScore;
        this.updatedAt = updatedAt;
    }

    /** The row a removed entry leaves behind: its key and name, nothing to borrow. */
    public static SponsorCatalogEntity removed(String sponsorKey, String name, long updatedAt) {
        SponsorCatalogEntity entity = new SponsorCatalogEntity(sponsorKey, name, List.of(), List.of(), null, updatedAt);
        entity.removed = true;
        return entity;
    }

    public String getSponsorKey() {
        return sponsorKey;
    }

    public String getName() {
        return name;
    }

    public List<String> aliasList() {
        return split(aliases);
    }

    public List<String> semanticTermList() {
        return split(semanticTerms);
    }

    public Double getMinScore() {
        return minScore;
    }

    public boolean isRemoved() {
        return removed;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    /** How long a term list is once joined for storage. */
    public static int storedLength(List<String> terms) {
        return join(terms).length();
    }

    private static String join(List<String> terms) {
        return terms == null ? "" : String.join(TERM_SEPARATOR, terms);
    }

    private static List<String> split(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return List.of(stored.split(TERM_SEPARATOR));
    }
}

package com.streamsense.sentimentservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

/** One row per streamer: the sponsor profile relevance scoring uses for that channel. */
@Entity
@Table(name = "sponsor_relevance_profiles")
public class SponsorRelevanceProfileEntity {

    private static final String TERM_SEPARATOR = "\n";

    @Id
    @Column(name = "streamer", nullable = false, length = 255)
    private String streamer;

    @Column(name = "sponsor", nullable = false, length = 255)
    private String sponsor;

    @Column(name = "aliases", nullable = false, length = 4000)
    private String aliases = "";

    @Column(name = "semantic_terms", nullable = false, length = 4000)
    private String semanticTerms = "";

    @Column(name = "min_score")
    private Double minScore;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    protected SponsorRelevanceProfileEntity() {}

    public SponsorRelevanceProfileEntity(
            String streamer,
            String sponsor,
            List<String> aliases,
            List<String> semanticTerms,
            Double minScore,
            long updatedAt) {
        this.streamer = streamer;
        this.sponsor = sponsor;
        this.aliases = join(aliases);
        this.semanticTerms = join(semanticTerms);
        this.minScore = minScore;
        this.updatedAt = updatedAt;
    }

    public String getStreamer() {
        return streamer;
    }

    public String getSponsor() {
        return sponsor;
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

    public long getUpdatedAt() {
        return updatedAt;
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

package com.streamsense.sentimentservice.service;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.SponsorCatalogEntry;
import com.streamsense.sentimentservice.dto.SponsorCatalogUpdateRequest;
import com.streamsense.sentimentservice.dto.SponsorRelevanceProfile;
import com.streamsense.sentimentservice.dto.SponsorRelevanceUpdateRequest;
import com.streamsense.sentimentservice.persistence.SponsorRelevanceProfileEntity;
import com.streamsense.sentimentservice.persistence.SponsorRelevanceProfileRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * The sponsor profile relevance scoring uses per streamer. Profiles live in the database and are
 * mirrored in memory for the hot path; the configured seeds only fill in streamers that have no
 * stored profile yet, so an operator's or a deal's choice survives a restart. A profile keeps the
 * sponsor's name as the deal or the operator spelled it (the reports match mentions by that name)
 * and borrows the aliases and terms of the catalog entry that name reaches, by name or by alias.
 */
@Service
public class SponsorRelevanceProfileService {

    private final StreamSenseProperties properties;
    private final SponsorRelevanceProfileRepository repository;
    private final SponsorCatalogService catalog;
    private final ConcurrentMap<String, SponsorRelevanceProfile> activeProfiles = new ConcurrentHashMap<>();

    public SponsorRelevanceProfileService(
            StreamSenseProperties properties,
            SponsorRelevanceProfileRepository repository,
            SponsorCatalogService catalog) {
        this.properties = properties;
        this.repository = repository;
        this.catalog = catalog;
    }

    @PostConstruct
    public void seedConfiguredProfiles() {
        for (SponsorRelevanceProfileEntity stored : repository.findAll()) {
            SponsorRelevanceProfile profile = toProfile(stored);
            activeProfiles.put(normalize(stored.getStreamer()), profile);
            // A profile written before its catalog entry existed, or before the entry gained a term, catches up
            // here; the merge keeps the channel's own terms, so this is the same as saving it again.
            List<String> aliases = mergedTerms(configuredAliases(profile.getSponsor()), profile.getAliases());
            List<String> terms = mergedTerms(configuredSemanticTerms(profile.getSponsor()), profile.getSemanticTerms());
            if (!aliases.equals(profile.getAliases()) || !terms.equals(profile.getSemanticTerms())) {
                SponsorRelevanceUpdateRequest refresh = new SponsorRelevanceUpdateRequest();
                refresh.setStreamer(profile.getStreamer());
                refresh.setSponsor(profile.getSponsor());
                refresh.setAliases(profile.getAliases());
                refresh.setSemanticTerms(profile.getSemanticTerms());
                refresh.setMinScore(profile.getMinScore());
                update(refresh);
            }
        }
        for (StreamSenseProperties.Seed seed :
                properties.getSentiment().getRelevance().getSeeds()) {
            if (clean(seed.getStreamer()) == null || clean(seed.getSponsor()) == null) {
                continue;
            }
            if (activeProfiles.containsKey(normalize(seed.getStreamer()))) {
                continue;
            }
            SponsorRelevanceUpdateRequest request = new SponsorRelevanceUpdateRequest();
            request.setStreamer(seed.getStreamer());
            request.setSponsor(seed.getSponsor());
            request.setMinScore(seed.getMinScore());
            update(request);
        }
    }

    public Optional<SponsorRelevanceProfile> findActive(String streamer) {
        if (!properties.getSentiment().getRelevance().isEnabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeProfiles.get(normalize(streamer)));
    }

    /** Every stored profile, by streamer. */
    public List<SponsorRelevanceProfile> list() {
        return activeProfiles.values().stream()
                .sorted(Comparator.comparing(SponsorRelevanceProfile::getStreamer))
                .toList();
    }

    public SponsorRelevanceProfile update(SponsorRelevanceUpdateRequest request) {
        SponsorRelevanceProfile profile = new SponsorRelevanceProfile();
        profile.setStreamer(normalize(request.getStreamer()));
        profile.setSponsor(clean(request.getSponsor()));
        profile.setAliases(mergedTerms(configuredAliases(profile.getSponsor()), request.getAliases()));
        profile.setSemanticTerms(
                mergedTerms(configuredSemanticTerms(profile.getSponsor()), request.getSemanticTerms()));
        profile.setMinScore(
                request.getMinScore() != null
                        ? request.getMinScore()
                        : properties.getSentiment().getRelevance().getMinScore());
        repository.save(new SponsorRelevanceProfileEntity(
                profile.getStreamer(),
                profile.getSponsor(),
                profile.getAliases(),
                profile.getSemanticTerms(),
                profile.getMinScore(),
                System.currentTimeMillis()));
        activeProfiles.put(profile.getStreamer(), profile);
        return profile;
    }

    public void clear() {
        repository.deleteAll();
        activeProfiles.clear();
    }

    /**
     * Replaces a catalog entry and brings the profiles that reach it up to date: each such profile
     * gains the entry's aliases and terms (what it already had stays, so a channel's own additions
     * survive a catalog edit; a term removed from the catalog is removed from the channel by hand).
     */
    public SponsorCatalogEntry updateCatalogEntry(SponsorCatalogUpdateRequest request) {
        SponsorCatalogEntry entry = catalog.upsert(request);
        for (SponsorRelevanceProfile profile : activeProfiles.values()) {
            if (catalog.find(profile.getSponsor())
                    .filter(found -> found.name().equals(entry.name()))
                    .isEmpty()) {
                continue;
            }
            SponsorRelevanceUpdateRequest refresh = new SponsorRelevanceUpdateRequest();
            refresh.setStreamer(profile.getStreamer());
            refresh.setSponsor(profile.getSponsor());
            refresh.setAliases(profile.getAliases());
            refresh.setSemanticTerms(profile.getSemanticTerms());
            refresh.setMinScore(profile.getMinScore());
            update(refresh);
        }
        return entry;
    }

    private static SponsorRelevanceProfile toProfile(SponsorRelevanceProfileEntity stored) {
        SponsorRelevanceProfile profile = new SponsorRelevanceProfile();
        profile.setStreamer(stored.getStreamer());
        profile.setSponsor(stored.getSponsor());
        profile.setAliases(new ArrayList<>(stored.aliasList()));
        profile.setSemanticTerms(new ArrayList<>(stored.semanticTermList()));
        profile.setMinScore(stored.getMinScore());
        return profile;
    }

    private List<String> configuredAliases(String sponsor) {
        return catalog.find(sponsor).map(SponsorCatalogEntry::aliases).orElseGet(List::of);
    }

    private List<String> configuredSemanticTerms(String sponsor) {
        return catalog.find(sponsor).map(SponsorCatalogEntry::semanticTerms).orElseGet(List::of);
    }

    @SafeVarargs
    private final List<String> mergedTerms(List<String>... termGroups) {
        Set<String> terms = new LinkedHashSet<>();
        for (List<String> group : termGroups) {
            if (group == null) {
                continue;
            }
            for (String term : group) {
                String cleaned = clean(term);
                if (cleaned != null) {
                    terms.add(cleaned);
                }
            }
        }
        return new ArrayList<>(terms);
    }

    private String normalize(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "" : cleaned.toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

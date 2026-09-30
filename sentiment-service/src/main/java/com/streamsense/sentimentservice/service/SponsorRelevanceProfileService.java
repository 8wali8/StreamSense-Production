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
 * Its minimum score is the channel's own override when it has one, else the entry's, else the
 * configured default; only the override is stored.
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
                refresh(profile);
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

    /** Replaces the channel's profile. A request's {@code minScore} is the channel's override; absent, it has none. */
    public SponsorRelevanceProfile update(SponsorRelevanceUpdateRequest request) {
        SponsorRelevanceProfile profile = new SponsorRelevanceProfile();
        profile.setStreamer(normalize(request.getStreamer()));
        profile.setSponsor(clean(request.getSponsor()));
        profile.setAliases(mergedTerms(configuredAliases(profile.getSponsor()), request.getAliases()));
        profile.setSemanticTerms(
                mergedTerms(configuredSemanticTerms(profile.getSponsor()), request.getSemanticTerms()));
        profile.setMinScoreOverride(request.getMinScore());
        profile.setMinScore(scoreInEffect(profile.getSponsor(), request.getMinScore()));
        repository.save(new SponsorRelevanceProfileEntity(
                profile.getStreamer(),
                profile.getSponsor(),
                profile.getAliases(),
                profile.getSemanticTerms(),
                profile.getMinScoreOverride(),
                System.currentTimeMillis()));
        activeProfiles.put(profile.getStreamer(), profile);
        return profile;
    }

    public void clear() {
        repository.deleteAll();
        activeProfiles.clear();
    }

    /**
     * Replaces a catalog entry and brings the profiles on it up to date: the ones that reached the
     * entry before the edit (an edit may drop the very alias a profile spells the sponsor by) and the
     * ones that reach it after. Each gains the entry's aliases and terms and keeps its own, so a term
     * removed from the catalog is removed from a channel by hand; each follows the entry's minimum
     * score unless the channel has its own.
     */
    public SponsorCatalogEntry updateCatalogEntry(SponsorCatalogUpdateRequest request) {
        String key = normalize(request.getName());
        Set<String> onIt = new LinkedHashSet<>(streamersReaching(key));
        SponsorCatalogEntry entry = catalog.upsert(request);
        onIt.addAll(streamersReaching(key));
        for (String streamer : onIt) {
            SponsorRelevanceProfile profile = activeProfiles.get(streamer);
            if (profile == null) {
                continue;
            }
            // A profile whose spelling the edit dropped borrows the entry's terms once more, by the entry's name.
            profile.setAliases(mergedTerms(entry.aliases(), profile.getAliases()));
            profile.setSemanticTerms(mergedTerms(entry.semanticTerms(), profile.getSemanticTerms()));
            refresh(profile);
        }
        return entry;
    }

    /** The streamers whose profile's sponsor reaches the catalog entry of that key. */
    private List<String> streamersReaching(String key) {
        return activeProfiles.values().stream()
                .filter(profile -> catalog.find(profile.getSponsor())
                        .filter(found -> normalize(found.name()).equals(key))
                        .isPresent())
                .map(SponsorRelevanceProfile::getStreamer)
                .toList();
    }

    /** Saves a profile again as it stands, which borrows afresh from the catalog and keeps the channel's own choices. */
    private void refresh(SponsorRelevanceProfile profile) {
        SponsorRelevanceUpdateRequest request = new SponsorRelevanceUpdateRequest();
        request.setStreamer(profile.getStreamer());
        request.setSponsor(profile.getSponsor());
        request.setAliases(profile.getAliases());
        request.setSemanticTerms(profile.getSemanticTerms());
        request.setMinScore(profile.getMinScoreOverride());
        update(request);
    }

    private SponsorRelevanceProfile toProfile(SponsorRelevanceProfileEntity stored) {
        SponsorRelevanceProfile profile = new SponsorRelevanceProfile();
        profile.setStreamer(stored.getStreamer());
        profile.setSponsor(stored.getSponsor());
        profile.setAliases(new ArrayList<>(stored.aliasList()));
        profile.setSemanticTerms(new ArrayList<>(stored.semanticTermList()));
        profile.setMinScoreOverride(stored.getMinScore());
        profile.setMinScore(scoreInEffect(stored.getSponsor(), stored.getMinScore()));
        return profile;
    }

    /** The channel's override, else the score of the catalog entry the sponsor reaches, else the configured default. */
    private double scoreInEffect(String sponsor, Double override) {
        if (override != null) {
            return override;
        }
        return catalog.find(sponsor)
                .map(SponsorCatalogEntry::minScore)
                .orElseGet(() -> properties.getSentiment().getRelevance().getMinScore());
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

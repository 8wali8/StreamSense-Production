package com.streamsense.sentimentservice.service;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
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
 * stored profile yet, so an operator's or a deal's choice survives a restart.
 */
@Service
public class SponsorRelevanceProfileService {

    private final StreamSenseProperties properties;
    private final SponsorRelevanceProfileRepository repository;
    private final ConcurrentMap<String, SponsorRelevanceProfile> activeProfiles = new ConcurrentHashMap<>();

    public SponsorRelevanceProfileService(
            StreamSenseProperties properties, SponsorRelevanceProfileRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @PostConstruct
    public void seedConfiguredProfiles() {
        for (SponsorRelevanceProfileEntity stored : repository.findAll()) {
            activeProfiles.put(normalize(stored.getStreamer()), toProfile(stored));
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
        return configuredSponsor(sponsor)
                .map(StreamSenseProperties.Sponsor::getAliases)
                .orElseGet(List::of);
    }

    private List<String> configuredSemanticTerms(String sponsor) {
        return configuredSponsor(sponsor)
                .map(StreamSenseProperties.Sponsor::getSemanticTerms)
                .orElseGet(List::of);
    }

    private Optional<StreamSenseProperties.Sponsor> configuredSponsor(String sponsor) {
        String normalizedSponsor = normalize(sponsor);
        return properties.getSentiment().getRelevance().getSponsors().stream()
                .filter(candidate -> normalize(candidate.getName()).equals(normalizedSponsor))
                .findFirst();
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

package com.streamsense.sentimentservice.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.SponsorRelevanceProfile;
import com.streamsense.sentimentservice.dto.SponsorRelevanceUpdateRequest;

import jakarta.annotation.PostConstruct;

@Service
public class SponsorRelevanceProfileService {

    private final StreamSenseProperties properties;
    private final ConcurrentMap<String, SponsorRelevanceProfile> activeProfiles = new ConcurrentHashMap<>();

    public SponsorRelevanceProfileService(StreamSenseProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void seedConfiguredProfiles() {
        for (StreamSenseProperties.Seed seed : properties.getSentiment().getRelevance().getSeeds()) {
            if (clean(seed.getStreamer()) == null || clean(seed.getSponsor()) == null) {
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

    public SponsorRelevanceProfile update(SponsorRelevanceUpdateRequest request) {
        SponsorRelevanceProfile profile = new SponsorRelevanceProfile();
        profile.setStreamer(clean(request.getStreamer()));
        profile.setSponsor(clean(request.getSponsor()));
        profile.setAliases(mergedTerms(configuredAliases(profile.getSponsor()), request.getAliases()));
        profile.setSemanticTerms(mergedTerms(
                configuredSemanticTerms(profile.getSponsor()),
                request.getSemanticTerms(),
                campaignGoalTerms(request.getCampaignGoal())));
        profile.setMinScore(request.getMinScore() != null ? request.getMinScore() : properties.getSentiment().getRelevance().getMinScore());
        activeProfiles.put(normalize(profile.getStreamer()), profile);
        return profile;
    }

    public void clear() {
        activeProfiles.clear();
    }

    private java.util.List<String> configuredAliases(String sponsor) {
        return configuredSponsor(sponsor)
                .map(StreamSenseProperties.Sponsor::getAliases)
                .orElseGet(java.util.List::of);
    }

    private java.util.List<String> configuredSemanticTerms(String sponsor) {
        return configuredSponsor(sponsor)
                .map(StreamSenseProperties.Sponsor::getSemanticTerms)
                .orElseGet(java.util.List::of);
    }

    private Optional<StreamSenseProperties.Sponsor> configuredSponsor(String sponsor) {
        String normalizedSponsor = normalize(sponsor);
        return properties.getSentiment().getRelevance().getSponsors().stream()
                .filter(candidate -> normalize(candidate.getName()).equals(normalizedSponsor))
                .findFirst();
    }

    @SafeVarargs
    private final java.util.List<String> mergedTerms(java.util.List<String>... termGroups) {
        Set<String> terms = new LinkedHashSet<>();
        for (java.util.List<String> group : termGroups) {
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

    private java.util.List<String> campaignGoalTerms(String campaignGoal) {
        String cleaned = clean(campaignGoal);
        return cleaned == null ? java.util.List.of() : java.util.List.of(cleaned);
    }

    private String normalize(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "" : cleaned.toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

package com.streamsense.sentimentservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.SponsorRelevanceProfile;
import com.streamsense.sentimentservice.dto.SponsorRelevanceUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SponsorRelevanceProfileServiceTest {

    private StreamSenseProperties propertiesWithRedBullSeed() {
        StreamSenseProperties properties = new StreamSenseProperties();

        StreamSenseProperties.Sponsor redBull = new StreamSenseProperties.Sponsor();
        redBull.setName("Red Bull");
        redBull.setAliases(List.of("red bull", "redbull"));
        redBull.setSemanticTerms(List.of("energy drink", "wings"));
        properties.getSentiment().getRelevance().getSponsors().add(redBull);

        StreamSenseProperties.Seed seed = new StreamSenseProperties.Seed();
        seed.setStreamer("redbull-testing");
        seed.setSponsor("Red Bull");
        properties.getSentiment().getRelevance().getSeeds().add(seed);

        return properties;
    }

    @Test
    void seedConfiguredProfiles_activatesConfiguredStreamerProfile() {
        SponsorRelevanceProfileService service = new SponsorRelevanceProfileService(propertiesWithRedBullSeed());

        service.seedConfiguredProfiles();

        Optional<SponsorRelevanceProfile> active = service.findActive("redbull-testing");
        assertThat(active).isPresent();
        assertThat(active.get().getSponsor()).isEqualTo("Red Bull");
        assertThat(active.get().getAliases()).contains("red bull", "redbull");
        assertThat(active.get().getSemanticTerms()).contains("energy drink", "wings");
        assertThat(active.get().getMinScore())
                .isEqualTo(propertiesWithRedBullSeed()
                        .getSentiment()
                        .getRelevance()
                        .getMinScore());
    }

    @Test
    void seedConfiguredProfiles_usesSeedMinScoreOverride() {
        StreamSenseProperties properties = propertiesWithRedBullSeed();
        properties.getSentiment().getRelevance().getSeeds().get(0).setMinScore(0.75d);
        SponsorRelevanceProfileService service = new SponsorRelevanceProfileService(properties);

        service.seedConfiguredProfiles();

        assertThat(service.findActive("redbull-testing"))
                .isPresent()
                .get()
                .extracting(SponsorRelevanceProfile::getMinScore)
                .isEqualTo(0.75d);
    }

    @Test
    void seedConfiguredProfiles_skipsIncompleteSeeds() {
        StreamSenseProperties properties = propertiesWithRedBullSeed();
        StreamSenseProperties.Seed incomplete = new StreamSenseProperties.Seed();
        incomplete.setStreamer("   ");
        incomplete.setSponsor("Red Bull");
        properties.getSentiment().getRelevance().getSeeds().add(incomplete);
        SponsorRelevanceProfileService service = new SponsorRelevanceProfileService(properties);

        service.seedConfiguredProfiles();

        assertThat(service.findActive("redbull-testing")).isPresent();
        assertThat(service.findActive("")).isEmpty();
    }

    @Test
    void update_overridesSeededProfileForSameStreamer() {
        SponsorRelevanceProfileService service = new SponsorRelevanceProfileService(propertiesWithRedBullSeed());
        service.seedConfiguredProfiles();

        SponsorRelevanceUpdateRequest request = new SponsorRelevanceUpdateRequest();
        request.setStreamer("redbull-testing");
        request.setSponsor("Nike");
        service.update(request);

        assertThat(service.findActive("redbull-testing"))
                .isPresent()
                .get()
                .extracting(SponsorRelevanceProfile::getSponsor)
                .isEqualTo("Nike");
    }
}

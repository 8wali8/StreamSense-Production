package com.streamsense.sentimentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.SponsorCatalogUpdateRequest;
import com.streamsense.sentimentservice.dto.SponsorRelevanceProfile;
import com.streamsense.sentimentservice.dto.SponsorRelevanceUpdateRequest;
import com.streamsense.sentimentservice.persistence.SponsorCatalogRepository;
import com.streamsense.sentimentservice.persistence.SponsorRelevanceProfileEntity;
import com.streamsense.sentimentservice.persistence.SponsorRelevanceProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SponsorRelevanceProfileServiceTest {

    private final SponsorRelevanceProfileRepository repository = mock(SponsorRelevanceProfileRepository.class);
    private final SponsorCatalogRepository catalogRepository = mock(SponsorCatalogRepository.class);

    /** The profile service over a catalog seeded from the same properties, as at start-up. */
    private SponsorRelevanceProfileService service(StreamSenseProperties properties) {
        when(catalogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SponsorCatalogService catalog = new SponsorCatalogService(properties, catalogRepository);
        catalog.seedConfiguredSponsors();
        return new SponsorRelevanceProfileService(properties, repository, catalog);
    }

    @Test
    void aSponsorNamedByAliasBorrowsTheCatalogEntrysTerms() {
        SponsorRelevanceProfileService service = service(propertiesWithRedBullSeed());
        SponsorRelevanceUpdateRequest request = new SponsorRelevanceUpdateRequest();
        request.setStreamer("8wali8");
        request.setSponsor("redbull");

        SponsorRelevanceProfile profile = service.update(request);

        // The profile keeps the deal's spelling (reports match mentions by it) and gains the entry's terms.
        assertThat(profile.getSponsor()).isEqualTo("redbull");
        assertThat(profile.getAliases()).containsExactly("red bull", "redbull");
        assertThat(profile.getSemanticTerms()).containsExactly("energy drink", "wings");
    }

    @Test
    void aStoredProfileWrittenBeforeItsCatalogEntryCatchesUpAtStartUp() {
        when(repository.findAll())
                .thenReturn(List.of(
                        new SponsorRelevanceProfileEntity("8wali8", "redbull", List.of(), List.of("can"), 0.5d, 1L)));
        SponsorRelevanceProfileService service = service(propertiesWithRedBullSeed());

        service.seedConfiguredProfiles();

        SponsorRelevanceProfile caughtUp = service.findActive("8wali8").orElseThrow();
        assertThat(caughtUp.getSponsor()).isEqualTo("redbull");
        assertThat(caughtUp.getAliases()).containsExactly("red bull", "redbull");
        assertThat(caughtUp.getSemanticTerms()).containsExactly("energy drink", "wings", "can");
        assertThat(caughtUp.getMinScore()).isEqualTo(0.5d);
    }

    @Test
    void aCatalogEditReachesTheProfilesOnThatSponsorAndKeepsTheirOwnTerms() {
        SponsorRelevanceProfileService service = service(propertiesWithRedBullSeed());
        SponsorRelevanceUpdateRequest pointed = new SponsorRelevanceUpdateRequest();
        pointed.setStreamer("8wali8");
        pointed.setSponsor("redbull");
        pointed.setSemanticTerms(List.of("can"));
        service.update(pointed);
        SponsorRelevanceUpdateRequest other = new SponsorRelevanceUpdateRequest();
        other.setStreamer("ninja");
        other.setSponsor("Nike");
        service.update(other);

        SponsorCatalogUpdateRequest edit = new SponsorCatalogUpdateRequest();
        edit.setName("Red Bull");
        edit.setAliases(List.of("redbull", "rb"));
        edit.setSemanticTerms(List.of("f1"));
        service.updateCatalogEntry(edit);

        SponsorRelevanceProfile refreshed = service.findActive("8wali8").orElseThrow();
        assertThat(refreshed.getAliases()).containsExactly("redbull", "rb", "red bull");
        assertThat(refreshed.getSemanticTerms()).containsExactly("f1", "energy drink", "wings", "can");
        assertThat(service.findActive("ninja").orElseThrow().getSemanticTerms()).isEmpty();
    }

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
        SponsorRelevanceProfileService service = service(propertiesWithRedBullSeed());

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
        SponsorRelevanceProfileService service = service(properties);

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
        SponsorRelevanceProfileService service = service(properties);

        service.seedConfiguredProfiles();

        assertThat(service.findActive("redbull-testing")).isPresent();
        assertThat(service.findActive("")).isEmpty();
    }

    @Test
    void seedConfiguredProfiles_leavesAStoredProfileAlone() {
        when(repository.findAll())
                .thenReturn(List.of(new SponsorRelevanceProfileEntity(
                        "redbull-testing", "Nike", List.of("nike"), List.of("shoes"), 0.6d, 1L)));
        SponsorRelevanceProfileService service = service(propertiesWithRedBullSeed());

        service.seedConfiguredProfiles();

        Optional<SponsorRelevanceProfile> active = service.findActive("redbull-testing");
        assertThat(active).isPresent();
        assertThat(active.get().getSponsor()).isEqualTo("Nike");
        assertThat(active.get().getAliases()).containsExactly("nike");
        assertThat(active.get().getMinScore()).isEqualTo(0.6d);
    }

    @Test
    void update_overridesSeededProfileForSameStreamer() {
        SponsorRelevanceProfileService service = service(propertiesWithRedBullSeed());
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

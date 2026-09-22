package com.streamsense.sentimentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.SponsorCatalogEntry;
import com.streamsense.sentimentservice.dto.SponsorCatalogUpdateRequest;
import com.streamsense.sentimentservice.persistence.SponsorCatalogEntity;
import com.streamsense.sentimentservice.persistence.SponsorCatalogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class SponsorCatalogServiceTest {

    private final SponsorCatalogRepository repository = mock(SponsorCatalogRepository.class);

    private static StreamSenseProperties propertiesWithRedBull() {
        StreamSenseProperties properties = new StreamSenseProperties();
        StreamSenseProperties.Sponsor redBull = new StreamSenseProperties.Sponsor();
        redBull.setName("Red Bull");
        redBull.setAliases(List.of("red bull", "redbull"));
        redBull.setSemanticTerms(List.of("energy drink", "wings"));
        properties.getSentiment().getRelevance().getSponsors().add(redBull);
        return properties;
    }

    private SponsorCatalogService service(StreamSenseProperties properties) {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SponsorCatalogService service = new SponsorCatalogService(properties, repository);
        service.seedConfiguredSponsors();
        return service;
    }

    @Test
    void seedsTheConfiguredSponsorsAndReachesAnEntryByNameOrAliasWhateverTheCase() {
        SponsorCatalogService service = service(propertiesWithRedBull());

        assertThat(service.list()).extracting(SponsorCatalogEntry::name).containsExactly("Red Bull");
        assertThat(service.find("RED BULL")).map(SponsorCatalogEntry::name).contains("Red Bull");
        assertThat(service.find("redbull"))
                .map(SponsorCatalogEntry::semanticTerms)
                .contains(List.of("energy drink", "wings"));
        assertThat(service.find("Prime")).isEmpty();
        assertThat(service.find("  ")).isEmpty();
    }

    @Test
    void aStoredEntryIsNotOverwrittenByTheConfiguredOne() {
        when(repository.findAll())
                .thenReturn(List.of(
                        new SponsorCatalogEntity("red bull", "Red Bull", List.of("rb"), List.of("f1"), 0.7d, 1L)));
        SponsorCatalogService service = service(propertiesWithRedBull());

        assertThat(service.find("Red Bull")).get().satisfies(entry -> {
            assertThat(entry.aliases()).containsExactly("rb");
            assertThat(entry.semanticTerms()).containsExactly("f1");
            assertThat(entry.minScore()).isEqualTo(0.7d);
        });
        verify(repository, never()).save(any());
    }

    @Test
    void anUpsertReplacesTheEntryWithTrimmedDistinctTermsAndADeleteRemovesIt() {
        SponsorCatalogService service = service(propertiesWithRedBull());
        SponsorCatalogUpdateRequest request = new SponsorCatalogUpdateRequest();
        request.setName(" red bull ");
        request.setAliases(List.of(" rb", "rb", " ", "redbull"));
        request.setSemanticTerms(List.of("f1"));
        request.setMinScore(0.6d);

        SponsorCatalogEntry entry = service.upsert(request);

        assertThat(entry.name()).isEqualTo("red bull");
        assertThat(entry.aliases()).containsExactly("rb", "redbull");
        assertThat(entry.semanticTerms()).containsExactly("f1");
        assertThat(service.list()).hasSize(1);
        assertThat(service.find("RB")).map(SponsorCatalogEntry::name).contains("red bull");
        assertThat(service.delete("Red Bull")).isTrue();
        assertThat(service.delete("Red Bull")).isFalse();
        assertThat(service.find("redbull")).isEmpty();
        verify(repository).deleteById("red bull");
    }
}

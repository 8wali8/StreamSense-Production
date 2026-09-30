package com.streamsense.videoservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamsense.videoservice.cache.RecentSponsorDetectionsCache;
import com.streamsense.videoservice.client.AnalyticsDependencyException;
import com.streamsense.videoservice.client.CurrentDeal;
import com.streamsense.videoservice.client.CurrentDealClient;
import com.streamsense.videoservice.client.MlEngineClient;
import com.streamsense.videoservice.config.StreamSenseProperties;
import com.streamsense.videoservice.dto.MlSponsorRequest;
import com.streamsense.videoservice.dto.MlSponsorResponse;
import com.streamsense.videoservice.events.FrameData;
import com.streamsense.videoservice.events.SponsorDetectionEvent;
import com.streamsense.videoservice.kafka.SponsorDetectionProducer;
import com.streamsense.videoservice.metrics.VideoMetrics;
import com.streamsense.videoservice.persistence.SponsorDetectionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** A frame's outcome follows the channel's deal: no logo means no ML call, a lookup failure means unavailable, a logo means the engine is asked about it. */
class VideoProcessingServiceTest {

    private final MlEngineClient engine = mock(MlEngineClient.class);
    private final SponsorDetectionRepository repository = mock(SponsorDetectionRepository.class);
    private final SponsorDetectionProducer producer = mock(SponsorDetectionProducer.class);
    private final RecentSponsorDetectionsCache cache = mock(RecentSponsorDetectionsCache.class);
    private final CurrentDealClient deals = mock(CurrentDealClient.class);

    private VideoProcessingService service(CurrentDealClient client) {
        @SuppressWarnings("unchecked")
        ObjectProvider<CurrentDealClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return new VideoProcessingService(
                engine,
                repository,
                producer,
                new VideoMetrics(new SimpleMeterRegistry()),
                new StreamSenseProperties(),
                cache,
                provider);
    }

    @Test
    void aChannelWithoutALogoIsNotSentToTheEngineAndIsRecordedAsNoLogo() {
        when(deals.find("racer")).thenReturn(Optional.of(new CurrentDeal(3, "Red Bull", List.of())));

        SponsorDetectionEvent event = service(deals).processFrame(frame(), "corr", null);

        verify(engine, never()).analyzeSponsor(any());
        assertThat(event.getOutcome()).isEqualTo("NO_LOGO");
        assertThat(event.getSponsor()).isEqualTo("Red Bull");
        assertThat(event.getDealId()).isEqualTo(3L);
        assertThat(event.getLogoId()).isNull();
        assertThat(event.getConfidence()).isZero();
        assertThat(event.getModelVersion()).isEqualTo("no-logo");
        assertThat(event.getFallback()).isFalse();
        verify(producer).publish(any(), anyString(), any());
    }

    @Test
    void aChannelWithNoDealAtAllIsNoLogoUnderTheUnknownSponsor() {
        when(deals.find("racer")).thenReturn(Optional.empty());

        SponsorDetectionEvent event = service(deals).processFrame(frame(), "corr", null);

        verify(engine, never()).analyzeSponsor(any());
        assertThat(event.getOutcome()).isEqualTo("NO_LOGO");
        assertThat(event.getSponsor()).isEqualTo("UNKNOWN");
        assertThat(event.getDealId()).isNull();
    }

    @Test
    void aDealWithALogoSendsTheSponsorAndTheLogoToTheEngineAndKeepsItsOutcome() {
        when(deals.find("racer"))
                .thenReturn(Optional.of(new CurrentDeal(
                        3,
                        "Red Bull",
                        List.of(
                                new CurrentDeal.Logo(7, "s3://streamsense-logos/deals/3/a.png"),
                                new CurrentDeal.Logo(8, "s3://streamsense-logos/deals/3/b.png")))));
        MlSponsorResponse missed = MlSponsorResponse.empty("Red Bull", "logo-match-v1", "NOT_DETECTED");
        when(engine.analyzeSponsor(any())).thenReturn(missed);

        SponsorDetectionEvent event = service(deals).processFrame(frame(), "corr", null);

        ArgumentCaptor<MlSponsorRequest> sent = ArgumentCaptor.forClass(MlSponsorRequest.class);
        verify(engine).analyzeSponsor(sent.capture());
        assertThat(sent.getValue().sponsor()).isEqualTo("Red Bull");
        assertThat(sent.getValue().dealId()).isEqualTo(3L);
        assertThat(sent.getValue().logoId()).isEqualTo(7L);
        assertThat(sent.getValue().logoRefs())
                .containsExactly("s3://streamsense-logos/deals/3/a.png", "s3://streamsense-logos/deals/3/b.png");
        assertThat(event.getOutcome()).isEqualTo("NOT_DETECTED");
        assertThat(event.getDealId()).isEqualTo(3L);
        assertThat(event.getLogoId()).isEqualTo(7L);
    }

    @Test
    void anEngineWithoutOutcomesIsReadAsDetectedAndItsFallbackAsUnavailable() {
        when(deals.find("racer"))
                .thenReturn(
                        Optional.of(new CurrentDeal(3, "Red Bull", List.of(new CurrentDeal.Logo(7, "s3://l/a.png")))));
        MlSponsorResponse old = MlSponsorResponse.empty("Red Bull", "stub-v1", null);
        old.setConfidence(0.9d);
        when(engine.analyzeSponsor(any())).thenReturn(old);
        assertThat(service(deals).processFrame(frame(), "corr", null).getOutcome())
                .isEqualTo("DETECTED");

        when(engine.analyzeSponsor(any())).thenReturn(MlSponsorResponse.empty("UNKNOWN", "fallback", null));
        SponsorDetectionEvent unavailable = service(deals).processFrame(frame(), "corr", null);
        assertThat(unavailable.getOutcome()).isEqualTo("UNAVAILABLE");
        assertThat(unavailable.getFallback()).isTrue();
    }

    @Test
    void aFailedDealLookupIsUnavailableNotAGuess() {
        when(deals.find("racer")).thenThrow(new AnalyticsDependencyException("analytics down", null));

        SponsorDetectionEvent event = service(deals).processFrame(frame(), "corr", null);

        verify(engine, never()).analyzeSponsor(any());
        assertThat(event.getOutcome()).isEqualTo("UNAVAILABLE");
        assertThat(event.getSponsor()).isEqualTo("UNKNOWN");
        assertThat(event.getModelVersion()).isEqualTo("fallback");
        assertThat(event.getFallback()).isTrue();
    }

    @Test
    void withoutAnAnalyticsServiceEveryFrameGoesToTheEngineAsBefore() {
        MlSponsorResponse found = MlSponsorResponse.empty("Nike", "stub-v1", "DETECTED");
        found.setConfidence(0.8d);
        when(engine.analyzeSponsor(any())).thenReturn(found);

        SponsorDetectionEvent event = service(null).processFrame(frame(), "corr", null);

        ArgumentCaptor<MlSponsorRequest> sent = ArgumentCaptor.forClass(MlSponsorRequest.class);
        verify(engine).analyzeSponsor(sent.capture());
        assertThat(sent.getValue().sponsor()).isNull();
        assertThat(sent.getValue().logoRefs()).isEmpty();
        assertThat(event.getOutcome()).isEqualTo("DETECTED");
        assertThat(event.getDealId()).isNull();
    }

    private static FrameData frame() {
        FrameData frame = new FrameData();
        frame.setFrameId("frame-1");
        frame.setStreamer("racer");
        frame.setFrameRef("s3://streamsense-frames/racer/s1/000001-frame-1.jpg");
        frame.setFrameSequence(1L);
        frame.setCapturedAt(System.currentTimeMillis() - 500);
        frame.setSource("TWITCH");
        return frame;
    }
}

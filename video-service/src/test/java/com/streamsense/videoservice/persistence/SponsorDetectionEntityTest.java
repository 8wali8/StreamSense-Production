package com.streamsense.videoservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.streamsense.videoservice.events.SponsorDetectionEvent;

class SponsorDetectionEntityTest {

    @Test
    void historyReadsCarryTheSameFallbackFlagThatWasPublished() {
        SponsorDetectionEvent published = new SponsorDetectionEvent();
        published.setDetectionEventId("det-1");
        published.setStreamer("test");
        published.setModelVersion("fallback");
        published.setFallback(true);

        SponsorDetectionEvent reloaded = SponsorDetectionEntity.fromEvent(published).toEvent();

        assertThat(reloaded.getFallback()).isTrue();
        assertThat(reloaded.getModelVersion()).isEqualTo("fallback");
    }

    @Test
    void realDetectionsAreNotFallbacks() {
        SponsorDetectionEvent published = new SponsorDetectionEvent();
        published.setDetectionEventId("det-2");
        published.setStreamer("test");
        published.setModelVersion("stub-v1");
        published.setFallback(false);

        assertThat(SponsorDetectionEntity.fromEvent(published).toEvent().getFallback()).isFalse();
    }
}

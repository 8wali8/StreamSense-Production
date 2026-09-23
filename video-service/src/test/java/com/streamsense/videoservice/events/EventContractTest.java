package com.streamsense.videoservice.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.videoservice.dto.MlSponsorRequest;
import com.streamsense.videoservice.dto.MlSponsorResponse;
import org.junit.jupiter.api.Test;

/** The frame events this service consumes and the detections it produces conform to docs/schemas. */
class EventContractTest {

    @Test
    void frameDataMatchesSchema() {
        FrameData frame = new FrameData();
        frame.setFrameId("frame-1");
        frame.setStreamer("streamer-1");
        frame.setFrameRef("frames/frame-1.png");
        frame.setFrameSequence(1L);
        frame.setCapturedAt(1710000000000L);
        frame.setSource("TWITCH");
        frame.setChannelLogin("streamer-1");
        frame.setStreamSessionId("streamer-1-1710000000000");
        frame.setTwitchStreamId("12345");
        frame.setVideoTimestampMs(0L);
        frame.setArtifactContentType("image/jpeg");
        frame.setArtifactSizeBytes(123L);
        frame.setCaptureWorkerId("worker-1");

        assertThat(EventSchemas.violations("frame-data.schema.json", EventSchemas.toJson(frame)))
                .isEmpty();
    }

    @Test
    void uploadedFrameWithoutSessionFieldsMatchesSchema() {
        FrameData frame = new FrameData();
        frame.setFrameId("frame-1");
        frame.setStreamer("streamer-1");
        frame.setFrameRef("frames/frame-1.png");
        frame.setFrameSequence(1L);
        frame.setCapturedAt(1710000000000L);

        assertThat(EventSchemas.violations("frame-data.schema.json", EventSchemas.toJson(frame)))
                .isEmpty();
    }

    @Test
    void sponsorDetectionMatchesSchema() {
        SponsorDetectionEvent event = detection();

        assertThat(EventSchemas.violations("sponsor-detection-event.schema.json", EventSchemas.toJson(event)))
                .isEmpty();
    }

    @Test
    void schemaRejectsAConfidenceAboveOne() {
        SponsorDetectionEvent event = detection();
        event.setConfidence(1.5d);

        assertThat(EventSchemas.violations("sponsor-detection-event.schema.json", EventSchemas.toJson(event)))
                .isNotEmpty();
    }

    private static SponsorDetectionEvent detection() {
        SponsorDetectionEvent event = new SponsorDetectionEvent();
        event.setDetectionEventId("det-1");
        event.setSourceFrameId("frame-1");
        event.setStreamer("streamer-1");
        event.setFrameRef("frames/frame-1.png");
        event.setFrameSequence(1L);
        event.setCapturedAt(1710000000000L);
        event.setProcessedAt(1710000000500L);
        event.setSponsor("Nike");
        event.setConfidence(0.91d);
        event.setModelVersion("stub-v1");
        event.setX(0.1d);
        event.setY(0.2d);
        event.setWidth(0.3d);
        event.setHeight(0.4d);
        event.setSource("TWITCH");
        event.setChannelLogin("streamer-1");
        event.setStreamSessionId("streamer-1-1710000000000");
        event.setTwitchStreamId("12345");
        event.setVideoTimestampMs(0L);
        event.setFallback(false);
        event.setOutcome("DETECTED");
        event.setDealId(3L);
        event.setLogoId(7L);
        return event;
    }

    @Test
    void everyOutcomeIsInTheSchemaAndAnUnknownOneIsNot() {
        for (DetectionOutcome outcome : DetectionOutcome.values()) {
            SponsorDetectionEvent event = detection();
            event.setOutcome(outcome.name());
            assertThat(EventSchemas.violations("sponsor-detection-event.schema.json", EventSchemas.toJson(event)))
                    .isEmpty();
        }
        SponsorDetectionEvent event = detection();
        event.setOutcome("MAYBE");
        assertThat(EventSchemas.violations("sponsor-detection-event.schema.json", EventSchemas.toJson(event)))
                .isNotEmpty();
    }

    @Test
    void theSponsorRequestAndResponseMatchTheMlEngineSchemas() {
        MlSponsorRequest request = new MlSponsorRequest(
                        "frame-1", "streamer-1", "s3://streamsense-frames/a.jpg", 1L, 1710000000000L)
                .forDeal("Red Bull", 3L, 7L, java.util.List.of("s3://streamsense-logos/deals/3/a.png"));
        assertThat(EventSchemas.violations("ml-sponsor-request.schema.json", EventSchemas.toJson(request)))
                .isEmpty();

        MlSponsorResponse response = MlSponsorResponse.empty("Red Bull", "logo-match-v1", "NOT_DETECTED");
        assertThat(EventSchemas.violations("ml-sponsor-response.schema.json", EventSchemas.toJson(response)))
                .isEmpty();
        // The engine's answer for the no-logo stand-in is never sent, but the shape without an outcome is the old
        // engine's.
        response.setOutcome(null);
        assertThat(EventSchemas.violations("ml-sponsor-response.schema.json", EventSchemas.toJson(response)))
                .isEmpty();
    }
}

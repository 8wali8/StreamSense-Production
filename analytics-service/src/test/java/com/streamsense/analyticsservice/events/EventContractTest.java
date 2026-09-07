package com.streamsense.analyticsservice.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Every event this service consumes deserialises from a schema-valid sample. The consumer parses with the
 * application ObjectMapper (Spring Boot's, which ignores unknown properties), so the samples carry every
 * property the producers may send, including the optional session fields the buckets are keyed by.
 */
class EventContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void chatMessageWithSessionFieldsDeserialises() throws Exception {
        JsonNode sample = EventSchemas.json(
                """
                {"eventId":"evt-1","streamer":"streamer-1","user":"user-1","message":"hello","timestamp":1710000000000,
                 "source":"TWITCH","channelLogin":"streamer-1","streamSessionId":"streamer-1-1710000000000",
                 "twitchStreamId":"12345"}
                """);
        assertThat(EventSchemas.violations("chat-message-event.schema.json", sample))
                .isEmpty();

        ChatMessageEvent event = MAPPER.treeToValue(sample, ChatMessageEvent.class);

        assertThat(event.getStreamSessionId()).isEqualTo("streamer-1-1710000000000");
        assertThat(event.getChannelLogin()).isEqualTo("streamer-1");
    }

    @Test
    void sentimentEventWithSessionFieldsDeserialises() throws Exception {
        JsonNode sample = EventSchemas.json(
                """
                {"sentimentEventId":"sent-1","sourceEventId":"evt-1","streamer":"streamer-1","user":"user-1",
                 "message":"great stream","chatTimestamp":1710000000000,"processedAt":1710000000500,
                 "label":"POSITIVE","score":0.87,"modelVersion":"lexical-v1","sponsorRelevant":true,
                 "matchedSponsor":"Red Bull","matchedTerms":["red bull"],"relevanceScore":0.9,
                 "relevanceReason":"lexical","relevanceVersion":"relevance-v1",
                 "source":"TWITCH","channelLogin":"streamer-1","streamSessionId":"streamer-1-1710000000000",
                 "twitchStreamId":"12345"}
                """);
        assertThat(EventSchemas.violations("sentiment-analysis-event.schema.json", sample))
                .isEmpty();

        SentimentAnalysisEvent event = MAPPER.treeToValue(sample, SentimentAnalysisEvent.class);

        assertThat(event.getLabel()).isEqualTo("POSITIVE");
        assertThat(event.getStreamSessionId()).isEqualTo("streamer-1-1710000000000");
    }

    @Test
    void transcriptSentimentEventDeserialises() throws Exception {
        JsonNode sample = EventSchemas.json(
                """
                {"sentimentEventId":"tsent-1","segmentId":"seg-1","streamer":"streamer-1","text":"welcome back",
                 "segmentStartedAt":1710000000000,"segmentEndedAt":1710000005000,"processedAt":1710000005500,
                 "label":"NEUTRAL","score":0.0,"modelVersion":"lexical-v1","transcriptModelVersion":"faster-whisper-base",
                 "streamSessionId":"streamer-1-1710000000000","transcriptSequence":3,
                 "sponsorRelevant":false,"matchedSponsor":null,"matchedTerms":[],"relevanceScore":0.0,
                 "relevanceReason":null,"relevanceVersion":null}
                """);
        assertThat(EventSchemas.violations("transcript-sentiment-event.schema.json", sample))
                .isEmpty();

        TranscriptSentimentEvent event = MAPPER.treeToValue(sample, TranscriptSentimentEvent.class);

        assertThat(event.getSegmentId()).isEqualTo("seg-1");
        assertThat(event.getTranscriptSequence()).isEqualTo(3L);
    }

    @Test
    void sponsorDetectionEventDeserialises() throws Exception {
        JsonNode sample = EventSchemas.json(
                """
                {"detectionEventId":"det-1","sourceFrameId":"frame-1","streamer":"streamer-1",
                 "frameRef":"frames/frame-1.png","frameSequence":1,"capturedAt":1710000000000,
                 "processedAt":1710000000500,"sponsor":"UNKNOWN","confidence":0.0,"modelVersion":"fallback",
                 "x":0.0,"y":0.0,"width":0.0,"height":0.0,"source":"TWITCH","channelLogin":"streamer-1",
                 "streamSessionId":"streamer-1-1710000000000","twitchStreamId":"12345","videoTimestampMs":0,
                 "fallback":true}
                """);
        assertThat(EventSchemas.violations("sponsor-detection-event.schema.json", sample))
                .isEmpty();

        SponsorDetectionEvent event = MAPPER.treeToValue(sample, SponsorDetectionEvent.class);

        assertThat(event.getSponsor()).isEqualTo("UNKNOWN");
        assertThat(event.getFallback()).isTrue();
    }
}

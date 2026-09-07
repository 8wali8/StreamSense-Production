package com.streamsense.apigateway.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.JacksonUtils;

/** Every event the gateway streams to subscribers deserialises from a schema-valid sample with Spring Kafka's mapper. */
class EventContractTest {

    private static final ObjectMapper KAFKA_MAPPER = JacksonUtils.enhancedObjectMapper();

    @Test
    void chatMessageDeserialises() throws Exception {
        JsonNode sample = EventSchemas.json(
                """
                {"eventId":"evt-1","streamer":"streamer-1","user":"user-1","message":"hello","timestamp":1710000000000,
                 "source":"TWITCH","channelLogin":"streamer-1","streamSessionId":"streamer-1-1710000000000",
                 "twitchStreamId":"12345"}
                """);
        assertThat(EventSchemas.violations("chat-message-event.schema.json", sample))
                .isEmpty();

        ChatMessageEvent event = KAFKA_MAPPER.treeToValue(sample, ChatMessageEvent.class);

        assertThat(event.getMessage()).isEqualTo("hello");
        assertThat(event.getStreamSessionId()).isEqualTo("streamer-1-1710000000000");
    }

    @Test
    void sentimentEventDeserialises() throws Exception {
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

        SentimentAnalysisEvent event = KAFKA_MAPPER.treeToValue(sample, SentimentAnalysisEvent.class);

        assertThat(event.getMatchedTerms()).containsExactly("red bull");
        assertThat(event.getStreamSessionId()).isEqualTo("streamer-1-1710000000000");
    }

    @Test
    void transcriptSegmentDeserialises() throws Exception {
        JsonNode sample = EventSchemas.json(
                """
                {"segmentId":"seg-1","streamer":"streamer-1","text":"welcome back","startedAt":1710000000000,
                 "endedAt":1710000005000,"language":"en","confidence":0.92,"modelVersion":"faster-whisper-base",
                 "source":"TWITCH_VOD_REPLAY","channelLogin":"streamer-1","streamSessionId":"streamer-1-1710000000000",
                 "twitchStreamId":null,"videoTimestampMs":15000,"transcriptSequence":3,"captureWorkerId":"worker-1"}
                """);
        assertThat(EventSchemas.violations("transcript-segment-event.schema.json", sample))
                .isEmpty();

        TranscriptSegmentEvent event = KAFKA_MAPPER.treeToValue(sample, TranscriptSegmentEvent.class);

        assertThat(event.getText()).isEqualTo("welcome back");
        assertThat(event.getConfidence()).isEqualTo(0.92d);
    }

    @Test
    void transcriptSentimentDeserialises() throws Exception {
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

        TranscriptSentimentEvent event = KAFKA_MAPPER.treeToValue(sample, TranscriptSentimentEvent.class);

        assertThat(event.getLabel()).isEqualTo("NEUTRAL");
        assertThat(event.isSponsorRelevant()).isFalse();
    }

    @Test
    void sponsorDetectionDeserialises() throws Exception {
        JsonNode sample = EventSchemas.json(
                """
                {"detectionEventId":"det-1","sourceFrameId":"frame-1","streamer":"streamer-1",
                 "frameRef":"frames/frame-1.png","frameSequence":1,"capturedAt":1710000000000,
                 "processedAt":1710000000500,"sponsor":"Nike","confidence":0.91,"modelVersion":"stub-v1",
                 "x":0.1,"y":0.2,"width":0.3,"height":0.4,"source":"TWITCH","channelLogin":"streamer-1",
                 "streamSessionId":"streamer-1-1710000000000","twitchStreamId":"12345","videoTimestampMs":0,
                 "fallback":false}
                """);
        assertThat(EventSchemas.violations("sponsor-detection-event.schema.json", sample))
                .isEmpty();

        SponsorDetectionEvent event = KAFKA_MAPPER.treeToValue(sample, SponsorDetectionEvent.class);

        assertThat(event.getSponsor()).isEqualTo("Nike");
        assertThat(event.getConfidence()).isEqualTo(0.91d);
    }
}

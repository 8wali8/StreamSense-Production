package com.streamsense.sentimentservice.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.JacksonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.sentimentservice.dto.MlSentimentRequest;
import com.streamsense.sentimentservice.dto.MlSentimentResponse;

/**
 * The events this service consumes deserialise from schema-valid samples with the mapper Spring Kafka uses, and
 * the events and ml-engine payloads it produces validate against their schemas under docs/schemas.
 */
class EventContractTest {

    /** The mapper Spring Kafka's JsonDeserializer builds when none is supplied. */
    private static final ObjectMapper KAFKA_MAPPER = JacksonUtils.enhancedObjectMapper();

    private static final String CHAT_SAMPLE = """
            {"eventId":"evt-1","streamer":"streamer-1","user":"user-1","message":"hello","timestamp":1710000000000,
             "source":"TWITCH_VOD_REPLAY","channelLogin":"streamer-1","streamSessionId":"streamer-1-1710000000000",
             "twitchStreamId":"12345"}
            """;

    private static final String SEGMENT_SAMPLE = """
            {"segmentId":"seg-1","streamer":"streamer-1","text":"welcome back","startedAt":1710000000000,
             "endedAt":1710000005000,"language":"en","confidence":0.92,"modelVersion":"faster-whisper-base",
             "source":"TWITCH_VOD_REPLAY","channelLogin":"streamer-1","streamSessionId":"streamer-1-1710000000000",
             "twitchStreamId":null,"videoTimestampMs":15000,"transcriptSequence":3,"captureWorkerId":"worker-1"}
            """;

    @Test
    void consumedChatMessageSampleIsSchemaValidAndDeserialises() throws Exception {
        JsonNode sample = EventSchemas.json(CHAT_SAMPLE);
        assertThat(EventSchemas.violations("chat-message-event.schema.json", sample)).isEmpty();

        ChatMessageEvent event = KAFKA_MAPPER.treeToValue(sample, ChatMessageEvent.class);

        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getSource()).isEqualTo("TWITCH_VOD_REPLAY");
        assertThat(event.getStreamSessionId()).isEqualTo("streamer-1-1710000000000");
    }

    @Test
    void consumedTranscriptSegmentSampleIsSchemaValidAndDeserialises() throws Exception {
        JsonNode sample = EventSchemas.json(SEGMENT_SAMPLE);
        assertThat(EventSchemas.violations("transcript-segment-event.schema.json", sample)).isEmpty();

        TranscriptSegmentEvent event = KAFKA_MAPPER.treeToValue(sample, TranscriptSegmentEvent.class);

        assertThat(event.getSegmentId()).isEqualTo("seg-1");
        assertThat(event.getSource()).isEqualTo("TWITCH_VOD_REPLAY");
        assertThat(event.getTranscriptSequence()).isEqualTo(3L);
    }

    @Test
    void producedSentimentEventMatchesSchema() {
        SentimentAnalysisEvent event = fullSentimentEvent();
        event.setSource("TWITCH");
        event.setChannelLogin("streamer-1");
        event.setStreamSessionId("streamer-1-1710000000000");
        event.setTwitchStreamId("12345");

        assertThat(EventSchemas.violations("sentiment-analysis-event.schema.json", EventSchemas.toJson(event))).isEmpty();
    }

    @Test
    void producedSentimentEventWithoutSessionFieldsStillMatchesSchema() {
        SentimentAnalysisEvent event = fullSentimentEvent();

        assertThat(EventSchemas.violations("sentiment-analysis-event.schema.json", EventSchemas.toJson(event))).isEmpty();
    }

    @Test
    void schemaRejectsAnUnknownSentimentLabel() {
        SentimentAnalysisEvent event = fullSentimentEvent();
        event.setLabel("MIXED");

        assertThat(EventSchemas.violations("sentiment-analysis-event.schema.json", EventSchemas.toJson(event))).isNotEmpty();
    }

    @Test
    void producedTranscriptSentimentEventMatchesSchema() {
        TranscriptSentimentEvent event = new TranscriptSentimentEvent();
        event.setSentimentEventId("tsent-1");
        event.setSegmentId("seg-1");
        event.setStreamer("streamer-1");
        event.setText("welcome back");
        event.setSegmentStartedAt(1710000000000L);
        event.setSegmentEndedAt(1710000005000L);
        event.setProcessedAt(1710000005500L);
        event.setLabel("POSITIVE");
        event.setScore(0.6d);
        event.setModelVersion("cardiffnlp/twitter-roberta-base-sentiment-latest");
        event.setTranscriptModelVersion("faster-whisper-base");
        event.setStreamSessionId("streamer-1-1710000000000");
        event.setTranscriptSequence(3L);
        event.setSponsorRelevant(false);
        event.setMatchedTerms(List.of());
        event.setRelevanceScore(0.0d);

        assertThat(EventSchemas.violations("transcript-sentiment-event.schema.json", EventSchemas.toJson(event))).isEmpty();
    }

    @Test
    void mlEnginePayloadsMatchTheirSchemas() {
        MlSentimentRequest request = new MlSentimentRequest("evt-1", "streamer-1", "user-1", "hello", 1710000000000L);
        MlSentimentResponse response = new MlSentimentResponse();
        response.setLabel("NEGATIVE");
        response.setScore(-0.4d);
        response.setModelVersion("lexical-v1");

        assertThat(EventSchemas.violations("ml-sentiment-request.schema.json", EventSchemas.toJson(request))).isEmpty();
        assertThat(EventSchemas.violations("ml-sentiment-response.schema.json", EventSchemas.toJson(response))).isEmpty();
    }

    private static SentimentAnalysisEvent fullSentimentEvent() {
        SentimentAnalysisEvent event = new SentimentAnalysisEvent();
        event.setSentimentEventId("sent-1");
        event.setSourceEventId("evt-1");
        event.setStreamer("streamer-1");
        event.setUser("user-1");
        event.setMessage("great stream");
        event.setChatTimestamp(1710000000000L);
        event.setProcessedAt(1710000000500L);
        event.setLabel("POSITIVE");
        event.setScore(0.87d);
        event.setModelVersion("cardiffnlp/twitter-roberta-base-sentiment-latest");
        event.setSponsorRelevant(true);
        event.setMatchedSponsor("Red Bull");
        event.setMatchedTerms(List.of("red bull"));
        event.setRelevanceScore(0.9d);
        event.setRelevanceReason("lexical");
        event.setRelevanceVersion("relevance-v1");
        return event;
    }
}

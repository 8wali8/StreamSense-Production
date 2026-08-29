package com.streamsense.apigateway.consumer;

import com.streamsense.apigateway.events.TranscriptSegmentEvent;
import com.streamsense.apigateway.events.TranscriptSentimentEvent;
import com.streamsense.apigateway.subscriptions.TranscriptSubscriptionBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TranscriptKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TranscriptKafkaConsumer.class);

    private final TranscriptSubscriptionBus bus;

    public TranscriptKafkaConsumer(TranscriptSubscriptionBus bus) {
        this.bus = bus;
    }

    @KafkaListener(
            topics = "${streamsense.topics.transcriptSegments:stream.transcript.segments}",
            containerFactory = "transcriptSegmentKafkaListenerContainerFactory")
    public void onTranscriptSegment(TranscriptSegmentEvent event) {
        log.info(
                "gateway consumed transcript segmentId={} streamer={} textLength={}",
                event.getSegmentId(),
                event.getStreamer(),
                event.getText() != null ? event.getText().length() : 0);
        bus.publishTranscript(event);
    }

    @KafkaListener(
            topics = "${streamsense.topics.transcriptSentimentEvents:stream.transcript.sentiment.events}",
            containerFactory = "transcriptSentimentKafkaListenerContainerFactory")
    public void onTranscriptSentiment(TranscriptSentimentEvent event) {
        log.info(
                "gateway consumed transcript sentimentEventId={} streamer={} label={}",
                event.getSentimentEventId(),
                event.getStreamer(),
                event.getLabel());
        bus.publishSentiment(event);
    }
}

package com.streamsense.apigateway.subscriptions;

import com.streamsense.apigateway.events.TranscriptSegmentEvent;
import com.streamsense.apigateway.events.TranscriptSentimentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class TranscriptSubscriptionBus {

    private static final Logger log = LoggerFactory.getLogger(TranscriptSubscriptionBus.class);

    private final Sinks.Many<TranscriptSegmentEvent> transcriptSink =
            Sinks.many().replay().latest();
    private final Sinks.Many<TranscriptSentimentEvent> sentimentSink =
            Sinks.many().replay().latest();

    public void publishTranscript(TranscriptSegmentEvent event) {
        Sinks.EmitResult result = transcriptSink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn(
                    "failed to emit transcript segmentId={} streamer={} result={}",
                    event.getSegmentId(),
                    event.getStreamer(),
                    result);
        }
    }

    public void publishSentiment(TranscriptSentimentEvent event) {
        Sinks.EmitResult result = sentimentSink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn(
                    "failed to emit transcript sentimentEventId={} streamer={} result={}",
                    event.getSentimentEventId(),
                    event.getStreamer(),
                    result);
        }
    }

    public Flux<TranscriptSegmentEvent> transcriptFlux() {
        return transcriptSink.asFlux();
    }

    public Flux<TranscriptSentimentEvent> sentimentFlux() {
        return sentimentSink.asFlux();
    }
}

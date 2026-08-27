package com.streamsense.apigateway.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.streamsense.apigateway.events.SentimentAnalysisEvent;
import com.streamsense.apigateway.subscriptions.SentimentSubscriptionBus;

@Component
public class SentimentKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(SentimentKafkaConsumer.class);

    private final SentimentSubscriptionBus bus;

    public SentimentKafkaConsumer(SentimentSubscriptionBus bus) {
        this.bus = bus;
    }

    @KafkaListener(topics = "${streamsense.topics.sentimentEvents}", containerFactory = "sentimentKafkaListenerContainerFactory")
    public void onMessage(SentimentAnalysisEvent event) {
        log.info("gateway consumed sentimentEventId={} streamer={} label={}",
                event.getSentimentEventId(), event.getStreamer(), event.getLabel());
        bus.publish(event);
    }
}

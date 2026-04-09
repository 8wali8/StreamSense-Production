package com.streamsense.apigateway.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.streamsense.apigateway.events.SponsorDetectionEvent;
import com.streamsense.apigateway.subscriptions.SponsorSubscriptionBus;

@Component
public class SponsorDetectionKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(SponsorDetectionKafkaConsumer.class);

    private final SponsorSubscriptionBus bus;

    public SponsorDetectionKafkaConsumer(SponsorSubscriptionBus bus) {
        this.bus = bus;
    }

    @KafkaListener(topics = "${streamsense.topics.sponsorDetections}", containerFactory = "sponsorKafkaListenerContainerFactory")
    public void onMessage(SponsorDetectionEvent event) {
        log.info("gateway consumed sponsor detectionEventId={} streamer={} sponsor={}",
                event.getDetectionEventId(), event.getStreamer(), event.getSponsor());
        bus.publish(event);
    }
}

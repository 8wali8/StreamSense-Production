package com.streamsense.chatservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ConfigDebugRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigDebugRunner.class);

    private final StreamSenseProperties properties;

    public ConfigDebugRunner(StreamSenseProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        log.info("streamsense.topics.chatMessages={}", properties.getTopics().getChatMessages());
        log.info("streamsense.topics.sentimentEvents={}", properties.getTopics().getSentimentEvents());
        log.info("streamsense.ml.baseUrl={}", properties.getMl().getBaseUrl());
    }
}
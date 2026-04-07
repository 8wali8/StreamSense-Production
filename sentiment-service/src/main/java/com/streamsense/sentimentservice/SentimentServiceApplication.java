package com.streamsense.sentimentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.streamsense.sentimentservice.config.StreamSenseProperties;

@SpringBootApplication
@EnableConfigurationProperties(StreamSenseProperties.class)

public class SentimentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SentimentServiceApplication.class, args);
    }
}

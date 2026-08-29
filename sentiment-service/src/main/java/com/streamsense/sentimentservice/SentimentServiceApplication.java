package com.streamsense.sentimentservice;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StreamSenseProperties.class)
public class SentimentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SentimentServiceApplication.class, args);
    }
}

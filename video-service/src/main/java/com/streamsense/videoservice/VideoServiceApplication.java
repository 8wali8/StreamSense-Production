package com.streamsense.videoservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.streamsense.videoservice.config.StreamSenseProperties;

@SpringBootApplication
@EnableConfigurationProperties(StreamSenseProperties.class)

public class VideoServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoServiceApplication.class, args);
    }
}

package com.streamsense.sentimentservice.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import com.streamsense.sentimentservice.events.TranscriptSegmentEvent;
import com.streamsense.sentimentservice.events.TranscriptSentimentEvent;
import com.streamsense.sentimentservice.service.SentimentService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@RestController
@RequestMapping("/api/sentiment")
public class SentimentHistoryController {

    private final SentimentService sentimentService;
    private final StreamSenseProperties properties;

    public SentimentHistoryController(SentimentService sentimentService, StreamSenseProperties properties) {
        this.sentimentService = sentimentService;
        this.properties = properties;
    }

    @GetMapping("/recent")
    public List<SentimentAnalysisEvent> recent(
            @RequestParam("streamer") @NotBlank String streamer,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(100) Integer limit) {
        int requestedLimit = limit != null ? limit : properties.getHistory().getDefaultLimit();
        return sentimentService.getRecentSentiment(streamer, requestedLimit);
    }

    @GetMapping("/transcript/recent")
    public List<TranscriptSegmentEvent> recentTranscript(
            @RequestParam("streamer") @NotBlank String streamer,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(100) Integer limit) {
        int requestedLimit = limit != null ? limit : properties.getHistory().getDefaultLimit();
        return sentimentService.getRecentTranscriptSegments(streamer, requestedLimit);
    }

    @GetMapping("/transcript/sentiment/recent")
    public List<TranscriptSentimentEvent> recentTranscriptSentiment(
            @RequestParam("streamer") @NotBlank String streamer,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(100) Integer limit) {
        int requestedLimit = limit != null ? limit : properties.getHistory().getDefaultLimit();
        return sentimentService.getRecentTranscriptSentiment(streamer, requestedLimit);
    }
}

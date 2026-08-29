package com.streamsense.sentimentservice.controller;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.SponsorRelevanceProfile;
import com.streamsense.sentimentservice.dto.SponsorRelevanceUpdateRequest;
import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import com.streamsense.sentimentservice.events.TranscriptSegmentEvent;
import com.streamsense.sentimentservice.events.TranscriptSentimentEvent;
import com.streamsense.sentimentservice.service.SentimentService;
import com.streamsense.sentimentservice.service.SponsorRelevanceProfileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/sentiment")
public class SentimentHistoryController {

    private final SentimentService sentimentService;
    private final SponsorRelevanceProfileService sponsorRelevanceProfileService;
    private final StreamSenseProperties properties;

    public SentimentHistoryController(
            SentimentService sentimentService,
            SponsorRelevanceProfileService sponsorRelevanceProfileService,
            StreamSenseProperties properties) {
        this.sentimentService = sentimentService;
        this.sponsorRelevanceProfileService = sponsorRelevanceProfileService;
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

    @GetMapping("/sponsor/recent")
    public List<SentimentAnalysisEvent> recentSponsorSentiment(
            @RequestParam("streamer") @NotBlank String streamer,
            @RequestParam(value = "sponsor", required = false) String sponsor,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(100) Integer limit) {
        int requestedLimit = limit != null ? limit : properties.getHistory().getDefaultLimit();
        return sentimentService.getRecentSponsorSentiment(streamer, sponsor, requestedLimit);
    }

    @GetMapping("/transcript/sponsor/recent")
    public List<TranscriptSentimentEvent> recentSponsorTranscriptSentiment(
            @RequestParam("streamer") @NotBlank String streamer,
            @RequestParam(value = "sponsor", required = false) String sponsor,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(100) Integer limit) {
        int requestedLimit = limit != null ? limit : properties.getHistory().getDefaultLimit();
        return sentimentService.getRecentSponsorTranscriptSentiment(streamer, sponsor, requestedLimit);
    }

    @PostMapping("/relevance/sponsors")
    public SponsorRelevanceProfile updateSponsorRelevance(
            @RequestBody @jakarta.validation.Valid SponsorRelevanceUpdateRequest request) {
        return sponsorRelevanceProfileService.update(request);
    }
}

package com.streamsense.analyticsservice.controller;

import com.streamsense.analyticsservice.api.BrandSafetyMetrics;
import com.streamsense.analyticsservice.api.SponsorExposureMetric;
import com.streamsense.analyticsservice.api.StreamMetricBucket;
import com.streamsense.analyticsservice.api.StreamMetricsSummary;
import com.streamsense.analyticsservice.service.MetricQueryService;
import com.streamsense.analyticsservice.service.MetricQueryService.QueryWindow;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/analytics/streams/{streamer}")
public class AnalyticsController {

    private final MetricQueryService metricQueryService;

    public AnalyticsController(MetricQueryService metricQueryService) {
        this.metricQueryService = metricQueryService;
    }

    @GetMapping("/summary")
    public StreamMetricsSummary summary(
            @PathVariable("streamer") @NotBlank String streamer,
            @RequestParam(value = "streamSessionId", required = false) String streamSessionId,
            @RequestParam(value = "windowMinutes", required = false) @Min(1) @Max(1440) Integer windowMinutes,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "from", required = false) Long from,
            @RequestParam(value = "to", required = false) Long to,
            @RequestParam(value = "bucketSeconds", required = false) @Min(60) @Max(60) Integer bucketSeconds) {
        return metricQueryService.summary(
                window(streamer, streamSessionId, windowMinutes, bucketSeconds, sessionId, from, to));
    }

    @GetMapping("/timeseries")
    public List<StreamMetricBucket> timeseries(
            @PathVariable("streamer") @NotBlank String streamer,
            @RequestParam(value = "streamSessionId", required = false) String streamSessionId,
            @RequestParam(value = "windowMinutes", required = false) @Min(1) @Max(1440) Integer windowMinutes,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "from", required = false) Long from,
            @RequestParam(value = "to", required = false) Long to,
            @RequestParam(value = "bucketSeconds", required = false) @Min(60) @Max(60) Integer bucketSeconds) {
        return metricQueryService.timeseries(
                window(streamer, streamSessionId, windowMinutes, bucketSeconds, sessionId, from, to));
    }

    @GetMapping("/sponsors")
    public List<SponsorExposureMetric> sponsors(
            @PathVariable("streamer") @NotBlank String streamer,
            @RequestParam(value = "streamSessionId", required = false) String streamSessionId,
            @RequestParam(value = "windowMinutes", required = false) @Min(1) @Max(1440) Integer windowMinutes,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "from", required = false) Long from,
            @RequestParam(value = "to", required = false) Long to,
            @RequestParam(value = "bucketSeconds", required = false) @Min(60) @Max(60) Integer bucketSeconds) {
        return metricQueryService.sponsorExposure(
                window(streamer, streamSessionId, windowMinutes, bucketSeconds, sessionId, from, to));
    }

    @GetMapping("/risk")
    public BrandSafetyMetrics risk(
            @PathVariable("streamer") @NotBlank String streamer,
            @RequestParam(value = "streamSessionId", required = false) String streamSessionId,
            @RequestParam(value = "windowMinutes", required = false) @Min(1) @Max(1440) Integer windowMinutes,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "from", required = false) Long from,
            @RequestParam(value = "to", required = false) Long to,
            @RequestParam(value = "bucketSeconds", required = false) @Min(60) @Max(60) Integer bucketSeconds) {
        return metricQueryService
                .summary(window(streamer, streamSessionId, windowMinutes, bucketSeconds, sessionId, from, to))
                .risk();
    }

    private QueryWindow window(
            String streamer,
            String streamSessionId,
            Integer windowMinutes,
            Integer bucketSeconds,
            Long sessionId,
            Long from,
            Long to) {
        return metricQueryService.resolve(streamer, streamSessionId, windowMinutes, bucketSeconds, sessionId, from, to);
    }
}

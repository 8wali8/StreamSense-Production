package com.streamsense.analyticsservice.controller;

import com.streamsense.analyticsservice.service.MetricQueryService;
import com.streamsense.analyticsservice.web.BrandSafetyMetrics;
import com.streamsense.analyticsservice.web.SponsorExposureMetric;
import com.streamsense.analyticsservice.web.StreamMetricBucket;
import com.streamsense.analyticsservice.web.StreamMetricsSummary;
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
            @RequestParam(value = "windowMinutes", required = false) @Min(1) @Max(1440) Integer windowMinutes) {
        return metricQueryService.summary(streamer, streamSessionId, windowMinutes);
    }

    @GetMapping("/timeseries")
    public List<StreamMetricBucket> timeseries(
            @PathVariable("streamer") @NotBlank String streamer,
            @RequestParam(value = "streamSessionId", required = false) String streamSessionId,
            @RequestParam(value = "windowMinutes", required = false) @Min(1) @Max(1440) Integer windowMinutes,
            @RequestParam(value = "bucketSeconds", required = false) @Min(60) @Max(60) Integer bucketSeconds) {
        return metricQueryService.timeseries(streamer, streamSessionId, windowMinutes, bucketSeconds);
    }

    @GetMapping("/sponsors")
    public List<SponsorExposureMetric> sponsors(
            @PathVariable("streamer") @NotBlank String streamer,
            @RequestParam(value = "streamSessionId", required = false) String streamSessionId,
            @RequestParam(value = "windowMinutes", required = false) @Min(1) @Max(1440) Integer windowMinutes) {
        return metricQueryService.sponsorExposure(streamer, streamSessionId, windowMinutes);
    }

    @GetMapping("/risk")
    public BrandSafetyMetrics risk(
            @PathVariable("streamer") @NotBlank String streamer,
            @RequestParam(value = "streamSessionId", required = false) String streamSessionId,
            @RequestParam(value = "windowMinutes", required = false) @Min(1) @Max(1440) Integer windowMinutes) {
        return metricQueryService.risk(streamer, streamSessionId, windowMinutes);
    }
}

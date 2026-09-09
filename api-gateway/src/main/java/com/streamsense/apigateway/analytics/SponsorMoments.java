package com.streamsense.apigateway.analytics;

import java.util.List;

/** The report timeline for one sponsor in one session. Offsets are from the session start. */
public record SponsorMoments(
        StreamSession session,
        String sponsor,
        List<ExposureSegment> segments,
        List<VoiceMention> voiceMentions,
        List<ChatMoment> chatMoments,
        List<RiskSpike> riskSpikes,
        HighlightMoment best,
        HighlightMoment weakest) {

    public record ExposureSegment(
            String sponsor,
            long startedAt,
            long endedAt,
            long offsetMs,
            long durationMs,
            Long videoTimestampMs,
            int detections,
            double peakConfidence) {}

    public record VoiceMention(
            String sentimentEventId, long at, long offsetMs, String label, double score, String text) {}

    public record ChatMoment(
            long at,
            long offsetMs,
            int count,
            double positiveShare,
            double negativeShare,
            double averageScore,
            String sample,
            String user) {}

    public record RiskSpike(long at, long offsetMs, Double chatNegativeRatio, long chatMessageCount) {}

    public record HighlightMoment(String kind, long at, long offsetMs, String title, String detail, double score) {}
}

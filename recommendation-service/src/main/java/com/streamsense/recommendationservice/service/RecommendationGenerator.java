package com.streamsense.recommendationservice.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.streamsense.recommendationservice.api.Recommendation;
import com.streamsense.recommendationservice.client.SentimentSignal;
import com.streamsense.recommendationservice.client.SponsorSignal;
import com.streamsense.recommendationservice.config.StreamSenseProperties;

public class RecommendationGenerator {

    private final Clock clock;

    public RecommendationGenerator() {
        this(Clock.systemUTC());
    }

    RecommendationGenerator(Clock clock) {
        this.clock = clock;
    }

    public List<Recommendation> generate(
            String streamer,
            int limit,
            List<SentimentSignal> sentimentSignals,
            List<SponsorSignal> sponsorSignals,
            String experimentName,
            String variantId,
            StreamSenseProperties.Variant variant) {
        SignalSummary summary = summarize(sentimentSignals, sponsorSignals);
        long generatedAt = Instant.now(clock).toEpochMilli();

        List<Recommendation> recommendations = new ArrayList<>();

        if (summary.totalSignals() == 0) {
            recommendations.add(new Recommendation(
                    recommendationId(streamer, "CAUTION_SIGNAL"),
                    streamer,
                    "Collect more signal before changing stream strategy",
                    "CAUTION_SIGNAL",
                    0.22d,
                    "Recent sentiment and sponsor activity are both sparse.",
                    List.of(
                            "No recent sentiment history was available.",
                            "No recent sponsor detections were available.",
                            "Hold changes until the stream has more observable activity."),
                    experimentName,
                    variantId,
                    generatedAt));

            return recommendations;
        }

        double momentumScore = clamp01(0.45d
                + (summary.averageSentimentScore() * 0.35d * variant.getPositiveWeight())
                + (summary.positiveRatio() * 0.25d * variant.getPositiveWeight())
                - (summary.negativeRatio() * 0.20d));
        if (momentumScore >= variant.getMomentumThreshold()) {
            recommendations.add(new Recommendation(
                    recommendationId(streamer, "CONTENT_MOMENTUM"),
                    streamer,
                    momentumScore >= 0.65d ? "Lean into high-energy moments" : "Keep momentum steady while sentiment is holding",
                    "CONTENT_MOMENTUM",
                    round(momentumScore),
                    String.format("Audience tone is %s with an average sentiment score of %.2f.",
                            summary.dominantSentimentLabel(), summary.averageSentimentScore()),
                    List.of(
                            String.format("Positive sentiment share is %.0f%% of the recent window.", summary.positiveRatio() * 100.0d),
                            String.format("Average sentiment score is %.2f across %d recent messages.", summary.averageSentimentScore(), summary.sentimentCount()),
                            String.format("The dominant audience tone is %s.", summary.dominantSentimentLabel())),
                    experimentName,
                    variantId,
                    generatedAt));
        }

        if (summary.topSponsor() != null) {
            double sponsorScore = clamp01(((summary.topSponsorFrequencyRatio() * 0.60d)
                    + (summary.topSponsorAverageConfidence() * 0.40d)) * variant.getSponsorWeight());
            recommendations.add(new Recommendation(
                    recommendationId(streamer, "SPONSOR_ALIGNMENT"),
                    streamer,
                    "Highlight " + summary.topSponsor() + " moments while they are landing",
                    "SPONSOR_ALIGNMENT",
                    round(sponsorScore),
                    String.format("%s is the most visible sponsor in the recent window.", summary.topSponsor()),
                    List.of(
                            String.format("%s appeared in %.0f%% of recent sponsor detections.", summary.topSponsor(), summary.topSponsorFrequencyRatio() * 100.0d),
                            String.format("Average confidence for %s was %.2f.", summary.topSponsor(), summary.topSponsorAverageConfidence()),
                            String.format("The active variant %s weights sponsor alignment at %.2f.", variantId, variant.getSponsorWeight())),
                    experimentName,
                    variantId,
                    generatedAt));
        }

        double toneScore = clamp01((summary.dominantToneStrength() * 0.70d)
                + (Math.max(0.0d, summary.averageSentimentScore()) * 0.30d));
        recommendations.add(new Recommendation(
                recommendationId(streamer, "AUDIENCE_TONE"),
                streamer,
                audienceToneTitle(summary.dominantSentimentLabel()),
                "AUDIENCE_TONE",
                round(toneScore),
                String.format("The audience is currently reading as %s.", summary.dominantSentimentLabel().toLowerCase()),
                List.of(
                        String.format("Positive=%d, neutral=%d, negative=%d in the recent sentiment window.",
                                summary.positiveCount(), summary.neutralCount(), summary.negativeCount()),
                        String.format("Dominant tone strength is %.0f%%.", summary.dominantToneStrength() * 100.0d),
                        String.format("Variant %s keeps audience-tone guidance visible even when sponsor data is sparse.", variantId)),
                experimentName,
                variantId,
                generatedAt));

        boolean sparseSignals = summary.sentimentCount() < 4 || summary.sponsorCount() < 2;
        if (summary.negativeRatio() >= 0.35d || sparseSignals) {
            double cautionScore = clamp01((summary.negativeRatio() * 0.75d * variant.getCautionWeight())
                    + (sparseSignals ? 0.25d : 0.0d));
            recommendations.add(new Recommendation(
                    recommendationId(streamer, "CAUTION_SIGNAL"),
                    streamer,
                    sparseSignals ? "Hold major changes until the stream has more signal" : "Watch for emerging audience fatigue",
                    "CAUTION_SIGNAL",
                    round(cautionScore),
                    sparseSignals
                            ? "The recent signal window is still thin, so large pivots would be noisy."
                            : "Negative sentiment is high enough to justify a caution flag.",
                    List.of(
                            String.format("Negative sentiment share is %.0f%%.", summary.negativeRatio() * 100.0d),
                            String.format("Recent window contains %d sentiment events and %d sponsor detections.", summary.sentimentCount(), summary.sponsorCount()),
                            String.format("Variant %s applies a caution weight of %.2f.", variantId, variant.getCautionWeight())),
                    experimentName,
                    variantId,
                    generatedAt));
        }

        return recommendations.stream()
                .sorted(Comparator.comparingDouble(Recommendation::score).reversed()
                        .thenComparing(Recommendation::category)
                        .thenComparing(Recommendation::title))
                .limit(limit)
                .toList();
    }

    private SignalSummary summarize(List<SentimentSignal> sentimentSignals, List<SponsorSignal> sponsorSignals) {
        int positiveCount = 0;
        int neutralCount = 0;
        int negativeCount = 0;
        double scoreTotal = 0.0d;
        for (SentimentSignal signal : sentimentSignals) {
            scoreTotal += signal.score();
            String label = signal.label() != null ? signal.label() : "NEUTRAL";
            if ("POSITIVE".equalsIgnoreCase(label)) {
                positiveCount += 1;
            } else if ("NEGATIVE".equalsIgnoreCase(label)) {
                negativeCount += 1;
            } else {
                neutralCount += 1;
            }
        }

        List<SponsorSignal> knownSponsors = sponsorSignals.stream()
                .filter(signal -> signal.sponsor() != null && !signal.sponsor().isBlank() && !"UNKNOWN".equalsIgnoreCase(signal.sponsor()))
                .toList();
        Map<String, List<SponsorSignal>> groupedSponsors = knownSponsors.stream()
                .collect(Collectors.groupingBy(SponsorSignal::sponsor, LinkedHashMap::new, Collectors.toList()));

        String topSponsor = null;
        double topSponsorFrequencyRatio = 0.0d;
        double topSponsorAverageConfidence = 0.0d;
        if (!groupedSponsors.isEmpty()) {
            Map.Entry<String, List<SponsorSignal>> topEntry = groupedSponsors.entrySet().stream()
                    .max(Comparator.<Map.Entry<String, List<SponsorSignal>>>comparingInt(entry -> entry.getValue().size())
                            .thenComparing(entry -> entry.getKey()))
                    .orElseThrow();
            topSponsor = topEntry.getKey();
            topSponsorFrequencyRatio = (double) topEntry.getValue().size() / (double) knownSponsors.size();
            topSponsorAverageConfidence = topEntry.getValue().stream()
                    .mapToDouble(SponsorSignal::confidence)
                    .average()
                    .orElse(0.0d);
        }

        int sentimentCount = sentimentSignals.size();
        int sponsorCount = sponsorSignals.size();
        String dominantSentimentLabel = dominantSentimentLabel(positiveCount, neutralCount, negativeCount);
        double dominantToneStrength = sentimentCount == 0 ? 0.0d : dominantSentimentCount(dominantSentimentLabel, positiveCount, neutralCount, negativeCount) / (double) sentimentCount;

        return new SignalSummary(
                sentimentCount,
                sponsorCount,
                positiveCount,
                neutralCount,
                negativeCount,
                sentimentCount == 0 ? 0.0d : scoreTotal / (double) sentimentCount,
                sentimentCount == 0 ? 0.0d : positiveCount / (double) sentimentCount,
                sentimentCount == 0 ? 0.0d : negativeCount / (double) sentimentCount,
                dominantSentimentLabel,
                dominantToneStrength,
                topSponsor,
                topSponsorFrequencyRatio,
                topSponsorAverageConfidence);
    }

    private int dominantSentimentCount(String label, int positiveCount, int neutralCount, int negativeCount) {
        return switch (label) {
            case "POSITIVE" -> positiveCount;
            case "NEGATIVE" -> negativeCount;
            default -> neutralCount;
        };
    }

    private String dominantSentimentLabel(int positiveCount, int neutralCount, int negativeCount) {
        if (positiveCount >= negativeCount && positiveCount >= neutralCount) {
            return "POSITIVE";
        }
        if (negativeCount >= positiveCount && negativeCount >= neutralCount) {
            return "NEGATIVE";
        }
        return "NEUTRAL";
    }

    private String audienceToneTitle(String label) {
        return switch (label) {
            case "POSITIVE" -> "Audience tone is positive enough to reward momentum";
            case "NEGATIVE" -> "Audience tone suggests a recovery moment";
            default -> "Audience tone is stable but not decisive";
        };
    }

    private String recommendationId(String streamer, String category) {
        return streamer + ":" + category.toLowerCase();
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private record SignalSummary(
            int sentimentCount,
            int sponsorCount,
            int positiveCount,
            int neutralCount,
            int negativeCount,
            double averageSentimentScore,
            double positiveRatio,
            double negativeRatio,
            String dominantSentimentLabel,
            double dominantToneStrength,
            String topSponsor,
            double topSponsorFrequencyRatio,
            double topSponsorAverageConfidence) {

        private int totalSignals() {
            return sentimentCount + sponsorCount;
        }
    }
}

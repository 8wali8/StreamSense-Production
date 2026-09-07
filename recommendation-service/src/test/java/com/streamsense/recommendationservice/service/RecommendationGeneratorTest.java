package com.streamsense.recommendationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.recommendationservice.api.Recommendation;
import com.streamsense.recommendationservice.client.SentimentSignal;
import com.streamsense.recommendationservice.client.SponsorSignal;
import com.streamsense.recommendationservice.config.StreamSenseProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationGeneratorTest {

    private final RecommendationGenerator generator =
            new RecommendationGenerator(Clock.fixed(Instant.parse("2026-04-12T03:00:00Z"), ZoneOffset.UTC));

    @Test
    void generatesDeterministicRecommendationsForSameInputs() {
        StreamSenseProperties.Variant variant = variant(1.0d, 0.9d, 1.1d);

        List<Recommendation> first = generator.generate(
                "test",
                3,
                positiveSentimentSignals(),
                sponsorSignals(),
                "recommendation-ranking-v1",
                "balanced",
                variant);
        List<Recommendation> second = generator.generate(
                "test",
                3,
                positiveSentimentSignals(),
                sponsorSignals(),
                "recommendation-ranking-v1",
                "balanced",
                variant);

        assertThat(first).containsExactlyElementsOf(second);
        assertThat(first)
                .extracting(Recommendation::category)
                .containsExactly("CONTENT_MOMENTUM", "SPONSOR_ALIGNMENT", "AUDIENCE_TONE");
    }

    @Test
    void sponsorBoostVariantRaisesSponsorRecommendationAboveBalanced() {
        List<SentimentSignal> sentimentSignals = positiveSentimentSignals();
        List<SponsorSignal> sponsorSignals = sponsorSignals();

        Recommendation balancedSponsorRecommendation = findCategory(
                generator.generate(
                        "test",
                        3,
                        sentimentSignals,
                        sponsorSignals,
                        "recommendation-ranking-v1",
                        "balanced",
                        variant(1.0d, 0.9d, 1.1d)),
                "SPONSOR_ALIGNMENT");
        Recommendation sponsorBoostRecommendation = findCategory(
                generator.generate(
                        "test",
                        3,
                        sentimentSignals,
                        sponsorSignals,
                        "recommendation-ranking-v1",
                        "sponsorBoost",
                        variant(0.85d, 1.25d, 1.0d)),
                "SPONSOR_ALIGNMENT");

        assertThat(sponsorBoostRecommendation.score()).isGreaterThan(balancedSponsorRecommendation.score());
    }

    @Test
    void returnsCautionRecommendationWhenSignalsAreSparse() {
        List<Recommendation> recommendations = generator.generate(
                "quiet-stream",
                3,
                List.of(),
                List.of(),
                "recommendation-ranking-v1",
                "balanced",
                variant(1.0d, 0.9d, 1.1d));

        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.getFirst().category()).isEqualTo("CAUTION_SIGNAL");
        assertThat(recommendations.getFirst().reasonSummary()).contains("sparse");
    }

    private Recommendation findCategory(List<Recommendation> recommendations, String category) {
        return recommendations.stream()
                .filter(recommendation -> category.equals(recommendation.category()))
                .findFirst()
                .orElseThrow();
    }

    private StreamSenseProperties.Variant variant(double positiveWeight, double sponsorWeight, double cautionWeight) {
        StreamSenseProperties.Variant variant = new StreamSenseProperties.Variant();
        variant.setPositiveWeight(positiveWeight);
        variant.setSponsorWeight(sponsorWeight);
        variant.setCautionWeight(cautionWeight);
        variant.setMomentumThreshold(0.15d);
        variant.setSponsorConfidenceThreshold(0.65d);
        return variant;
    }

    private List<SentimentSignal> positiveSentimentSignals() {
        return List.of(
                new SentimentSignal(
                        "sent-1",
                        "src-1",
                        "test",
                        "u1",
                        "great stream",
                        1710000000000L,
                        1710000000500L,
                        "POSITIVE",
                        0.82d,
                        "stub-v1"),
                new SentimentSignal(
                        "sent-2",
                        "src-2",
                        "test",
                        "u2",
                        "love this",
                        1710000001000L,
                        1710000001500L,
                        "POSITIVE",
                        0.73d,
                        "stub-v1"),
                new SentimentSignal(
                        "sent-3",
                        "src-3",
                        "test",
                        "u3",
                        "pretty good",
                        1710000002000L,
                        1710000002500L,
                        "NEUTRAL",
                        0.12d,
                        "stub-v1"));
    }

    private List<SponsorSignal> sponsorSignals() {
        return List.of(
                new SponsorSignal(
                        "det-1",
                        "frame-1",
                        "test",
                        "frames/1.png",
                        1.0d,
                        1710000000000L,
                        1710000000500L,
                        "Nike",
                        0.91d,
                        "stub-v1",
                        0.1d,
                        0.2d,
                        0.3d,
                        0.4d),
                new SponsorSignal(
                        "det-2",
                        "frame-2",
                        "test",
                        "frames/2.png",
                        2.0d,
                        1710000001000L,
                        1710000001500L,
                        "Nike",
                        0.88d,
                        "stub-v1",
                        0.1d,
                        0.2d,
                        0.3d,
                        0.4d),
                new SponsorSignal(
                        "det-3",
                        "frame-3",
                        "test",
                        "frames/3.png",
                        3.0d,
                        1710000002000L,
                        1710000002500L,
                        "Prime",
                        0.67d,
                        "stub-v1",
                        0.1d,
                        0.2d,
                        0.3d,
                        0.4d));
    }
}

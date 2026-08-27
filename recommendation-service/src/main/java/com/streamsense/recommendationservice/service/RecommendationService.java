package com.streamsense.recommendationservice.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.streamsense.recommendationservice.api.Recommendation;
import com.streamsense.recommendationservice.client.SentimentHistoryClient;
import com.streamsense.recommendationservice.client.SponsorHistoryClient;
import com.streamsense.recommendationservice.config.StreamSenseProperties;
import com.streamsense.recommendationservice.config.StreamSenseProperties.Recommendations;
import com.streamsense.recommendationservice.metrics.RecommendationMetrics;

@Service
public class RecommendationService {

    private final SentimentHistoryClient sentimentHistoryClient;
    private final SponsorHistoryClient sponsorHistoryClient;
    private final StreamSenseProperties properties;
    private final RecommendationMetrics recommendationMetrics;
    private final RecommendationGenerator recommendationGenerator;

    public RecommendationService(
            SentimentHistoryClient sentimentHistoryClient,
            SponsorHistoryClient sponsorHistoryClient,
            StreamSenseProperties properties,
            RecommendationMetrics recommendationMetrics) {
        this.sentimentHistoryClient = sentimentHistoryClient;
        this.sponsorHistoryClient = sponsorHistoryClient;
        this.properties = properties;
        this.recommendationMetrics = recommendationMetrics;
        this.recommendationGenerator = new RecommendationGenerator();
    }

    public List<Recommendation> recommendations(String streamer, Integer limit) {
        Recommendations recommendationProperties = properties.getRecommendations();
        int requestedLimit = limit != null ? Math.min(limit, recommendationProperties.getMaxLimit()) : recommendationProperties.getDefaultLimit();
        int signalWindowLimit = recommendationProperties.getSignalWindowLimit();
        String variantId = recommendationProperties.resolveActiveVariantId();
        StreamSenseProperties.Variant variant = recommendationProperties.resolveActiveVariant();

        return recommendationMetrics.recordGeneration(() -> {
            recommendationMetrics.incrementVariantSelection(recommendationProperties.getExperimentName(), variantId);
            List<Recommendation> recommendations = recommendationGenerator.generate(
                    streamer,
                    requestedLimit,
                    sentimentHistoryClient.recentSentiment(streamer, signalWindowLimit),
                    sponsorHistoryClient.recentDetections(streamer, signalWindowLimit),
                    recommendationProperties.getExperimentName(),
                    variantId,
                    variant);
            recommendationMetrics.incrementServed(recommendationProperties.getExperimentName(), variantId, recommendations.size());
            return recommendations;
        });
    }
}

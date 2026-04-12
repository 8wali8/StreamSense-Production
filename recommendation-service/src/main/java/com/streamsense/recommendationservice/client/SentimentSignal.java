package com.streamsense.recommendationservice.client;

public record SentimentSignal(
        String sentimentEventId,
        String sourceEventId,
        String streamer,
        String user,
        String message,
        long chatTimestamp,
        long processedAt,
        String label,
        double score,
        String modelVersion) {
}

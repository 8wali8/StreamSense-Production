package com.streamsense.sentimentservice.cache;

import java.util.List;
import java.util.Optional;

import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;

public interface RecentSentimentCache {

    Optional<List<SentimentAnalysisEvent>> find(String streamer, int limit);

    void put(String streamer, int limit, List<SentimentAnalysisEvent> recent);

    void evict(String streamer);
}

package com.streamsense.sentimentservice.cache;

import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import java.util.List;
import java.util.Optional;

public interface RecentSentimentCache {

    Optional<List<SentimentAnalysisEvent>> find(String streamer, int limit);

    void put(String streamer, int limit, List<SentimentAnalysisEvent> recent);

    void evict(String streamer);
}

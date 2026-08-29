package com.streamsense.videoservice.cache;

import com.streamsense.videoservice.events.SponsorDetectionEvent;
import java.util.List;
import java.util.Optional;

public interface RecentSponsorDetectionsCache {

    Optional<List<SponsorDetectionEvent>> find(String streamer, int limit);

    void put(String streamer, int limit, List<SponsorDetectionEvent> recent);

    void evict(String streamer);
}

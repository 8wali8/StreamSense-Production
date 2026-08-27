package com.streamsense.sentimentservice.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptSentimentRecordRepository extends JpaRepository<TranscriptSentimentRecordEntity, String> {

    List<TranscriptSentimentRecordEntity> findByStreamerOrderBySegmentEndedAtDesc(String streamer, Pageable pageable);

    List<TranscriptSentimentRecordEntity> findByStreamerAndSponsorRelevantTrueOrderBySegmentEndedAtDesc(String streamer, Pageable pageable);

    List<TranscriptSentimentRecordEntity> findByStreamerAndSponsorRelevantTrueAndMatchedSponsorIgnoreCaseOrderBySegmentEndedAtDesc(
            String streamer,
            String matchedSponsor,
            Pageable pageable);
}

package com.streamsense.sentimentservice.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptSentimentRecordRepository extends JpaRepository<TranscriptSentimentRecordEntity, String> {

    List<TranscriptSentimentRecordEntity> findByStreamerOrderBySegmentEndedAtDesc(String streamer, Pageable pageable);

    List<TranscriptSentimentRecordEntity> findByStreamerAndSponsorRelevantTrueOrderBySegmentEndedAtDesc(
            String streamer, Pageable pageable);

    List<TranscriptSentimentRecordEntity>
            findByStreamerAndSponsorRelevantTrueAndMatchedSponsorIgnoreCaseOrderBySegmentEndedAtDesc(
                    String streamer, String matchedSponsor, Pageable pageable);

    List<TranscriptSentimentRecordEntity>
            findByStreamerAndSponsorRelevantTrueAndSegmentEndedAtBetweenOrderBySegmentEndedAtAsc(
                    String streamer, long from, long to, Pageable pageable);

    List<TranscriptSentimentRecordEntity>
            findByStreamerAndSponsorRelevantTrueAndMatchedSponsorIgnoreCaseAndSegmentEndedAtBetweenOrderBySegmentEndedAtAsc(
                    String streamer, String matchedSponsor, long from, long to, Pageable pageable);
}

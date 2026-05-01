package com.streamsense.sentimentservice.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptSentimentRecordRepository extends JpaRepository<TranscriptSentimentRecordEntity, String> {

    List<TranscriptSentimentRecordEntity> findByStreamerOrderBySegmentEndedAtDesc(String streamer, Pageable pageable);
}

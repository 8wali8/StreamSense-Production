package com.streamsense.sentimentservice.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentimentRecordRepository extends JpaRepository<SentimentRecordEntity, String> {

    List<SentimentRecordEntity> findByStreamerOrderByChatTimestampDesc(String streamer, Pageable pageable);
}

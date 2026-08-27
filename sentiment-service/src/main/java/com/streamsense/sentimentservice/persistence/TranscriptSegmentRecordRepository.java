package com.streamsense.sentimentservice.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptSegmentRecordRepository extends JpaRepository<TranscriptSegmentRecordEntity, String> {

    List<TranscriptSegmentRecordEntity> findByStreamerOrderByEndedAtDesc(String streamer, Pageable pageable);
}

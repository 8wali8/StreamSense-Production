package com.streamsense.videoservice.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SponsorDetectionRepository extends JpaRepository<SponsorDetectionEntity, String> {

    List<SponsorDetectionEntity> findByStreamerOrderByCapturedAtDesc(String streamer, Pageable pageable);
}

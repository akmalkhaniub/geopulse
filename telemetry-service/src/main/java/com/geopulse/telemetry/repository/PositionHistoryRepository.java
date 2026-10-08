package com.geopulse.telemetry.repository;

import com.geopulse.telemetry.domain.PositionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PositionHistoryRepository extends JpaRepository<PositionHistory, Long> {

    boolean existsByMessageId(String messageId);

    Optional<PositionHistory> findTopByDeviceIdOrderByRecordedAtDesc(String deviceId);

    Page<PositionHistory> findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            String deviceId, Instant from, Instant to, Pageable pageable
    );
}

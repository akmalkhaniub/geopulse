package com.geopulse.alert.repository;

import com.geopulse.alert.domain.Alert;
import com.geopulse.alert.domain.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByOpenKey(String openKey);

    boolean existsByOpenKey(String openKey);

    Page<Alert> findByDeviceId(String deviceId, Pageable pageable);

    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    Page<Alert> findByDeviceIdAndStatus(String deviceId, AlertStatus status, Pageable pageable);
}

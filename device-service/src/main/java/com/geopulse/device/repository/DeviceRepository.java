package com.geopulse.device.repository;

import com.geopulse.device.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID>, JpaSpecificationExecutor<Device> {
    Optional<Device> findByIdAndDeletedFalse(UUID id);
    Optional<Device> findByExternalIdAndDeletedFalse(String externalId);
    Optional<Device> findByExternalId(String externalId);
    boolean existsByExternalIdAndDeletedFalse(String externalId);
}

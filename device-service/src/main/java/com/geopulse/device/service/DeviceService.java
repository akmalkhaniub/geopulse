package com.geopulse.device.service;

import com.geopulse.common.aspect.Audited;
import com.geopulse.common.error.ApiException;
import com.geopulse.common.event.DeviceStatusEvent;
import com.geopulse.common.event.TelemetryEvent;
import com.geopulse.common.event.Topics;
import com.geopulse.device.domain.ConnectivityStatus;
import com.geopulse.device.domain.Device;
import com.geopulse.device.domain.DeviceStatus;
import com.geopulse.device.domain.DeviceType;
import com.geopulse.device.dto.DeviceDTOs.CreateDeviceRequest;
import com.geopulse.device.dto.DeviceDTOs.DeviceLiveStatus;
import com.geopulse.device.dto.DeviceDTOs.DeviceResponse;
import com.geopulse.device.dto.DeviceDTOs.UpdateDeviceRequest;
import com.geopulse.device.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class DeviceService {

    public static final String REDIS_REGISTERED_SET = "devices:registered";

    private final DeviceRepository deviceRepository;
    private final StringRedisTemplate redis;

    public DeviceService(DeviceRepository deviceRepository, StringRedisTemplate redis) {
        this.deviceRepository = deviceRepository;
        this.redis = redis;
    }

    @Transactional
    @Audited(action = "DEVICE_CREATED")
    public DeviceResponse create(CreateDeviceRequest req) {
        if (deviceRepository.existsByExternalIdAndDeletedFalse(req.externalId())) {
            throw ApiException.conflict("device-exists", "Device with external ID already exists: " + req.externalId());
        }

        Device d = new Device();
        d.setExternalId(req.externalId());
        d.setName(req.name());
        d.setType(req.type());
        d.setOwnerMemberId(req.ownerMemberId());
        d.setStatus(DeviceStatus.ACTIVE);
        d.setConnectivity(ConnectivityStatus.OFFLINE);
        d.setDeleted(false);

        Device saved = deviceRepository.save(d);

        try {
            redis.opsForSet().add(REDIS_REGISTERED_SET, saved.getExternalId());
        } catch (Exception e) {
            log.warn("Redis unavailable for registration caching: {}", e.getMessage());
        }

        return DeviceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(UUID id) {
        return DeviceResponse.from(loadActive(id));
    }

    @Transactional(readOnly = true)
    public DeviceResponse getByExternalId(String externalId) {
        Device d = deviceRepository.findByExternalIdAndDeletedFalse(externalId)
                .orElseThrow(() -> ApiException.notFound("Device externalId " + externalId));
        return DeviceResponse.from(d);
    }

    @Transactional
    @Audited(action = "DEVICE_UPDATED", entityIdParam = "id")
    public DeviceResponse update(UUID id, UpdateDeviceRequest req) {
        Device d = loadActive(id);
        if (req.name() != null && !req.name().isBlank()) {
            d.setName(req.name());
        }
        if (req.type() != null) {
            d.setType(req.type());
        }
        if (req.status() != null) {
            d.setStatus(req.status());
        }
        if (req.ownerMemberId() != null) {
            d.setOwnerMemberId(req.ownerMemberId());
        }
        return DeviceResponse.from(deviceRepository.save(d));
    }

    @Transactional
    @Audited(action = "DEVICE_DELETED", entityIdParam = "id")
    public void delete(UUID id) {
        Device d = loadActive(id);
        d.setDeleted(true);
        d.setStatus(DeviceStatus.INACTIVE);
        deviceRepository.save(d);

        try {
            redis.opsForSet().remove(REDIS_REGISTERED_SET, d.getExternalId());
        } catch (Exception e) {
            log.warn("Redis unavailable for registration eviction: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public DeviceLiveStatus getLiveStatus(UUID id) {
        Device d = loadActive(id);
        return new DeviceLiveStatus(
                d.getExternalId(),
                d.getConnectivity(),
                d.getLastLat(),
                d.getLastLng(),
                d.getLastBatteryPct(),
                d.getLastSeenAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<DeviceResponse> list(DeviceStatus status, DeviceType type, Pageable pageable) {
        Specification<Device> spec = (root, q, cb) -> cb.isFalse(root.get("deleted"));
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (type != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), type));
        }
        return deviceRepository.findAll(spec, pageable).map(DeviceResponse::from);
    }

    @Transactional
    @KafkaListener(topics = Topics.TELEMETRY_PROCESSED, groupId = "device-service")
    public void onTelemetryProcessed(TelemetryEvent event) {
        deviceRepository.findByExternalIdAndDeletedFalse(event.deviceId()).ifPresent(device -> {
            if (device.getLastSeenAt() == null || event.recordedAt().isAfter(device.getLastSeenAt())) {
                device.setLastSeenAt(event.recordedAt());
                device.setLastLat(event.lat());
                device.setLastLng(event.lng());
                device.setLastBatteryPct(event.batteryPct());
                device.setConnectivity(ConnectivityStatus.ONLINE);
                deviceRepository.save(device);
            }
        });
    }

    @Transactional
    @KafkaListener(topics = Topics.DEVICE_STATUS, groupId = "device-service")
    public void onDeviceStatus(DeviceStatusEvent event) {
        deviceRepository.findByExternalIdAndDeletedFalse(event.deviceId()).ifPresent(device -> {
            ConnectivityStatus newConn = DeviceStatusEvent.STATUS_ONLINE.equalsIgnoreCase(event.status())
                    ? ConnectivityStatus.ONLINE
                    : ConnectivityStatus.OFFLINE;
            device.setConnectivity(newConn);
            deviceRepository.save(device);
        });
    }

    private Device loadActive(UUID id) {
        return deviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound("Device " + id));
    }
}

package com.geopulse.device.dto;

import com.geopulse.device.domain.ConnectivityStatus;
import com.geopulse.device.domain.Device;
import com.geopulse.device.domain.DeviceStatus;
import com.geopulse.device.domain.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class DeviceDTOs {

    public record CreateDeviceRequest(
            @NotBlank @Size(max = 64) String externalId,
            @NotBlank @Size(max = 100) String name,
            @NotNull DeviceType type,
            @Size(max = 64) String ownerMemberId
    ) {
    }

    public record UpdateDeviceRequest(
            @Size(max = 100) String name,
            DeviceType type,
            DeviceStatus status,
            @Size(max = 64) String ownerMemberId
    ) {
    }

    public record DeviceResponse(
            UUID id,
            String externalId,
            String name,
            DeviceType type,
            DeviceStatus status,
            ConnectivityStatus connectivity,
            String ownerMemberId,
            Instant lastSeenAt,
            Double lastLat,
            Double lastLng,
            Integer lastBatteryPct,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static DeviceResponse from(Device d) {
            return new DeviceResponse(
                    d.getId(),
                    d.getExternalId(),
                    d.getName(),
                    d.getType(),
                    d.getStatus(),
                    d.getConnectivity(),
                    d.getOwnerMemberId(),
                    d.getLastSeenAt(),
                    d.getLastLat(),
                    d.getLastLng(),
                    d.getLastBatteryPct(),
                    d.getCreatedAt(),
                    d.getUpdatedAt()
            );
        }
    }

    public record DeviceLiveStatus(
            String deviceId,
            ConnectivityStatus connectivity,
            Double lat,
            Double lng,
            Integer batteryPct,
            Instant lastSeenAt
    ) {
    }
}

package com.geopulse.alert.dto;

import com.geopulse.alert.domain.Alert;
import com.geopulse.alert.domain.AlertSeverity;
import com.geopulse.alert.domain.AlertStatus;
import com.geopulse.alert.domain.AlertType;
import com.geopulse.alert.domain.Geofence;
import com.geopulse.alert.domain.GeofenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

public class AlertDTOs {

    public record AlertResponse(
            Long id,
            String deviceId,
            AlertType type,
            AlertSeverity severity,
            AlertStatus status,
            String message,
            Double lat,
            Double lng,
            String acknowledgedBy,
            Instant acknowledgedAt,
            String resolvedBy,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static AlertResponse from(Alert a) {
            return new AlertResponse(
                    a.getId(),
                    a.getDeviceId(),
                    a.getType(),
                    a.getSeverity(),
                    a.getStatus(),
                    a.getMessage(),
                    a.getLat(),
                    a.getLng(),
                    a.getAcknowledgedBy(),
                    a.getAcknowledgedAt(),
                    a.getResolvedBy(),
                    a.getResolvedAt(),
                    a.getCreatedAt(),
                    a.getUpdatedAt()
            );
        }
    }

    public record GeofenceRequest(
            @NotBlank(message = "Geofence name is required")
            String name,
            @NotNull(message = "Geofence type is required")
            GeofenceType type,
            Double centerLat,
            Double centerLng,
            Double radiusMeters,
            String polygonGeoJson,
            Boolean active
    ) {
    }

    public record GeofenceResponse(
            Long id,
            String name,
            GeofenceType type,
            Double centerLat,
            Double centerLng,
            Double radiusMeters,
            String polygonGeoJson,
            Boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static GeofenceResponse from(Geofence g) {
            return new GeofenceResponse(
                    g.getId(),
                    g.getName(),
                    g.getType(),
                    g.getCenterLat(),
                    g.getCenterLng(),
                    g.getRadiusMeters(),
                    g.getPolygonGeoJson(),
                    g.getActive(),
                    g.getCreatedAt(),
                    g.getUpdatedAt()
            );
        }
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.isFirst(),
                    page.isLast()
            );
        }
    }
}

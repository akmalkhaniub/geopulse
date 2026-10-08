package com.geopulse.telemetry.dto;

import com.geopulse.telemetry.domain.PositionHistory;

import java.time.Instant;

public class TelemetryDTOs {

    public record PositionRecord(
            Long id,
            String messageId,
            String deviceId,
            Double lat,
            Double lng,
            Double speedKmh,
            Double headingDeg,
            Integer batteryPct,
            Boolean sos,
            Instant recordedAt,
            Instant ingestedAt
    ) {
        public static PositionRecord from(PositionHistory p) {
            return new PositionRecord(
                    p.getId(),
                    p.getMessageId(),
                    p.getDeviceId(),
                    p.getLat(),
                    p.getLng(),
                    p.getSpeedKmh(),
                    p.getHeadingDeg(),
                    p.getBatteryPct(),
                    p.getSos(),
                    p.getRecordedAt(),
                    p.getIngestedAt()
            );
        }
    }

    public record PageResponse<T>(
            java.util.List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
        public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
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

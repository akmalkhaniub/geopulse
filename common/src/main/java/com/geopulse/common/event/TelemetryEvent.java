package com.geopulse.common.event;

import java.time.Instant;

/**
 * Normalized telemetry payload published to Kafka telemetry.raw and telemetry.processed.
 * deviceId = hardware externalId.
 * messageId ensures downstream idempotency.
 */
public record TelemetryEvent(
        String deviceId,
        double lat,
        double lng,
        double speedKmh,
        double headingDeg,
        int batteryPct,
        boolean sos,
        Instant recordedAt,
        String messageId
) {
}

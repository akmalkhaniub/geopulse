package com.geopulse.common.event;

import java.time.Instant;

/**
 * Emitted by alert-service on state transitions (geofence breach, SOS, low battery, offline).
 */
public record AlertEvent(
        String alertId,
        String deviceId,
        String type,
        String severity,
        String message,
        double lat,
        double lng,
        Instant raisedAt
) {
}

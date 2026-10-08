package com.geopulse.common.event;

import java.time.Instant;

/**
 * Emitted when a device's connectivity status shifts between ONLINE and OFFLINE.
 * Sources: LWT (MQTT disconnect), TIMEOUT (scheduled offline scan), TELEMETRY (active ping).
 */
public record DeviceStatusEvent(
        String deviceId,
        String status,
        String source,
        Instant at
) {
    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_OFFLINE = "OFFLINE";

    public static final String SOURCE_LWT = "LWT";
    public static final String SOURCE_TIMEOUT = "TIMEOUT";
    public static final String SOURCE_TELEMETRY = "TELEMETRY";
}

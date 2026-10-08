package com.geopulse.ingestion.validation;

import com.geopulse.common.event.TelemetryEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class TelemetryValidator {

    private final Clock clock;

    public TelemetryValidator(Clock clock) {
        this.clock = clock;
    }

    public record Result(boolean valid, String reason) {
        public static Result ok() {
            return new Result(true, null);
        }

        public static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    public Result validate(String topicDeviceId, TelemetryEvent event) {
        if (event == null) {
            return Result.fail("Missing event payload");
        }
        if (event.deviceId() == null || event.deviceId().isBlank()) {
            return Result.fail("Missing deviceId");
        }
        if (topicDeviceId != null && !topicDeviceId.equals(event.deviceId())) {
            return Result.fail("Topic deviceId (" + topicDeviceId + ") does not match payload deviceId (" + event.deviceId() + ")");
        }
        if (event.messageId() == null || event.messageId().isBlank()) {
            return Result.fail("Missing messageId for idempotency");
        }
        if (event.lat() < -90.0 || event.lat() > 90.0) {
            return Result.fail("Latitude out of range [-90, 90]: " + event.lat());
        }
        if (event.lng() < -180.0 || event.lng() > 180.0) {
            return Result.fail("Longitude out of range [-180, 180]: " + event.lng());
        }
        if (event.batteryPct() < 0 || event.batteryPct() > 100) {
            return Result.fail("Battery percentage out of range [0, 100]: " + event.batteryPct());
        }
        if (event.speedKmh() < 0.0) {
            return Result.fail("Speed cannot be negative: " + event.speedKmh());
        }
        if (event.headingDeg() < 0.0 || event.headingDeg() >= 360.0) {
            return Result.fail("Heading out of range [0, 360): " + event.headingDeg());
        }

        // Time window check: not > 1 min in the future, not > 24 hours in past (SOS exempt from age)
        if (event.recordedAt() == null) {
            return Result.fail("Missing recordedAt timestamp");
        }
        Instant now = clock.instant();
        if (event.recordedAt().isAfter(now.plus(Duration.ofMinutes(1)))) {
            return Result.fail("Timestamp too far in future (>1m): " + event.recordedAt());
        }
        if (!event.sos() && event.recordedAt().isBefore(now.minus(Duration.ofHours(24)))) {
            return Result.fail("Timestamp too old (>24h): " + event.recordedAt());
        }

        return Result.ok();
    }
}

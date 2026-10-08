package com.geopulse.ingestion.validation;

import com.geopulse.common.event.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryValidatorTest {

    private final Instant now = Instant.parse("2026-10-08T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private TelemetryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TelemetryValidator(clock);
    }

    private TelemetryEvent validEvent() {
        return new TelemetryEvent(
                "GPS-001", 37.7749, -122.4194, 45.0, 180.0,
                85, false, now.minusSeconds(10), "msg-123"
        );
    }

    @Test
    void acceptsValidPayload() {
        var res = validator.validate("GPS-001", validEvent());
        assertThat(res.valid()).isTrue();
    }

    @Test
    void rejectsDeviceIdMismatch() {
        var res = validator.validate("GPS-OTHER", validEvent());
        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).contains("does not match");
    }

    @Test
    void rejectsLatitudeOutOfRange() {
        var event = new TelemetryEvent("GPS-001", 95.0, -122.0, 40.0, 90.0, 50, false, now, "msg-1");
        var res = validator.validate("GPS-001", event);
        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).contains("Latitude");
    }

    @Test
    void rejectsLongitudeOutOfRange() {
        var event = new TelemetryEvent("GPS-001", 40.0, 195.0, 40.0, 90.0, 50, false, now, "msg-1");
        var res = validator.validate("GPS-001", event);
        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).contains("Longitude");
    }

    @Test
    void rejectsBatteryOutOfRange() {
        var event = new TelemetryEvent("GPS-001", 40.0, 10.0, 40.0, 90.0, 105, false, now, "msg-1");
        var res = validator.validate("GPS-001", event);
        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).contains("Battery");
    }

    @Test
    void rejectsNegativeSpeed() {
        var event = new TelemetryEvent("GPS-001", 40.0, 10.0, -5.0, 90.0, 50, false, now, "msg-1");
        var res = validator.validate("GPS-001", event);
        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).contains("Speed");
    }

    @Test
    void rejectsInvalidHeading() {
        var event = new TelemetryEvent("GPS-001", 40.0, 10.0, 20.0, 365.0, 50, false, now, "msg-1");
        var res = validator.validate("GPS-001", event);
        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).contains("Heading");
    }

    @Test
    void rejectsTimestampInFuture() {
        var event = new TelemetryEvent("GPS-001", 40.0, 10.0, 20.0, 90.0, 50, false, now.plus(Duration.ofMinutes(5)), "msg-1");
        var res = validator.validate("GPS-001", event);
        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).contains("future");
    }

    @Test
    void rejectsTimestampOlderThan24hWhenNotSos() {
        var event = new TelemetryEvent("GPS-001", 40.0, 10.0, 20.0, 90.0, 50, false, now.minus(Duration.ofHours(25)), "msg-1");
        var res = validator.validate("GPS-001", event);
        assertThat(res.valid()).isFalse();
        assertThat(res.reason()).contains("old");
    }

    @Test
    void acceptsTimestampOlderThan24hWhenSosTrue() {
        var event = new TelemetryEvent("GPS-001", 40.0, 10.0, 20.0, 90.0, 50, true, now.minus(Duration.ofHours(25)), "msg-1");
        var res = validator.validate("GPS-001", event);
        assertThat(res.valid()).isTrue();
    }
}

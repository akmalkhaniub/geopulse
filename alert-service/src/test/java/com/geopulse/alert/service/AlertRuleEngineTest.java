package com.geopulse.alert.service;

import com.geopulse.alert.domain.AlertSeverity;
import com.geopulse.alert.domain.AlertType;
import com.geopulse.alert.domain.Geofence;
import com.geopulse.alert.domain.GeofenceType;
import com.geopulse.alert.repository.GeofenceRepository;
import com.geopulse.common.event.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleEngineTest {

    @Mock
    private AlertService alertService;

    @Mock
    private GeofenceRepository geofenceRepository;

    @Mock
    private GeofenceEvaluator geofenceEvaluator;

    private AlertRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new AlertRuleEngine(alertService, geofenceRepository, geofenceEvaluator);
    }

    @Test
    void evaluateSos_triggersCriticalAlert() {
        TelemetryEvent event = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 60.0, 90.0, 80, true, Instant.now(), "msg-1"
        );

        ruleEngine.evaluateSos(event);

        verify(alertService).raiseAlertIfNotOpen(
                eq("GPS-001"),
                eq(AlertType.SOS),
                eq(AlertSeverity.CRITICAL),
                eq("Emergency SOS triggered by device GPS-001"),
                eq(37.77),
                eq(-122.41),
                eq("GPS-001:SOS")
        );
    }

    @Test
    void evaluateBattery_hysteresisThresholds() {
        TelemetryEvent lowBatteryEvent = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 60.0, 90.0, 10, false, Instant.now(), "msg-2"
        );

        ruleEngine.evaluateBattery(lowBatteryEvent);

        verify(alertService).raiseAlertIfNotOpen(
                eq("GPS-001"),
                eq(AlertType.LOW_BATTERY),
                eq(AlertSeverity.WARNING),
                eq("Battery level critical: 10%"),
                eq(37.77),
                eq(-122.41),
                eq("GPS-001:LOW_BATTERY")
        );

        // When charged back to 22% (>= 20% hysteresis threshold)
        TelemetryEvent rechargedEvent = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 60.0, 90.0, 22, false, Instant.now(), "msg-3"
        );

        ruleEngine.evaluateBattery(rechargedEvent);

        verify(alertService).resolveAlertByOpenKey("GPS-001:LOW_BATTERY", "auto-hysteresis");
    }

    @Test
    void evaluateSpeed_triggersAndClearsSpeedingAlert() {
        TelemetryEvent speedingEvent = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 135.0, 90.0, 80, false, Instant.now(), "msg-4"
        );

        ruleEngine.evaluateSpeed(speedingEvent);

        verify(alertService).raiseAlertIfNotOpen(
                eq("GPS-001"),
                eq(AlertType.SPEEDING),
                eq(AlertSeverity.WARNING),
                eq("Speed limit exceeded: 135 km/h (limit 120 km/h)"),
                eq(37.77),
                eq(-122.41),
                eq("GPS-001:SPEEDING")
        );

        // Under 100 km/h clears speeding alert
        TelemetryEvent normalSpeedEvent = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 85.0, 90.0, 80, false, Instant.now(), "msg-5"
        );

        ruleEngine.evaluateSpeed(normalSpeedEvent);

        verify(alertService).resolveAlertByOpenKey("GPS-001:SPEEDING", "auto-speed-normal");
    }

    @Test
    void evaluateGeofences_triggersBreachWhenEvaluatedTrue() {
        Geofence geofence = Geofence.builder()
                .id(10L)
                .name("Red Zone")
                .type(GeofenceType.DANGER_ZONE)
                .active(true)
                .build();

        when(geofenceRepository.findByActiveTrue()).thenReturn(List.of(geofence));
        when(geofenceEvaluator.isBreached(geofence, 37.77, -122.41)).thenReturn(true);

        TelemetryEvent event = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 50.0, 90.0, 80, false, Instant.now(), "msg-6"
        );

        ruleEngine.evaluateGeofences(event);

        verify(alertService).raiseAlertIfNotOpen(
                eq("GPS-001"),
                eq(AlertType.GEOFENCE_BREACH),
                eq(AlertSeverity.HIGH),
                eq("Geofence breached: Red Zone (DANGER_ZONE)"),
                eq(37.77),
                eq(-122.41),
                eq("GPS-001:GEOFENCE:10")
        );
    }

    @Test
    void evaluateOfflineClear_autoResolvesOfflineAlert() {
        TelemetryEvent event = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 50.0, 90.0, 80, false, Instant.now(), "msg-7"
        );

        ruleEngine.evaluateOfflineClear(event);

        verify(alertService).resolveAlertByOpenKey("GPS-001:OFFLINE", "auto-heartbeat");
    }
}

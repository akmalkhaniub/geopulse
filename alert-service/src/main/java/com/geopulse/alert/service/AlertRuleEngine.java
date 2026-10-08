package com.geopulse.alert.service;

import com.geopulse.alert.domain.AlertSeverity;
import com.geopulse.alert.domain.AlertType;
import com.geopulse.alert.domain.Geofence;
import com.geopulse.alert.repository.GeofenceRepository;
import com.geopulse.common.event.TelemetryEvent;
import com.geopulse.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleEngine.class);
    private static final double SPEED_LIMIT_KMH = 120.0;
    private static final double SPEED_CLEAR_KMH = 100.0;
    private static final int BATTERY_LOW_THRESHOLD = 15;
    private static final int BATTERY_CLEAR_THRESHOLD = 20;

    private final AlertService alertService;
    private final GeofenceRepository geofenceRepository;
    private final GeofenceEvaluator geofenceEvaluator;

    public AlertRuleEngine(AlertService alertService,
                           GeofenceRepository geofenceRepository,
                           GeofenceEvaluator geofenceEvaluator) {
        this.alertService = alertService;
        this.geofenceRepository = geofenceRepository;
        this.geofenceEvaluator = geofenceEvaluator;
    }

    @KafkaListener(topics = Topics.TELEMETRY_PROCESSED, groupId = "alert-engine-group")
    public void processTelemetry(TelemetryEvent event) {
        if (event == null || event.deviceId() == null) {
            return;
        }

        evaluateSos(event);
        evaluateBattery(event);
        evaluateSpeed(event);
        evaluateGeofences(event);
        evaluateOfflineClear(event);
    }

    public void evaluateSos(TelemetryEvent event) {
        if (event.sos()) {
            String openKey = event.deviceId() + ":SOS";
            alertService.raiseAlertIfNotOpen(
                    event.deviceId(),
                    AlertType.SOS,
                    AlertSeverity.CRITICAL,
                    "Emergency SOS triggered by device " + event.deviceId(),
                    event.lat(),
                    event.lng(),
                    openKey
            );
        }
    }

    public void evaluateBattery(TelemetryEvent event) {
        String openKey = event.deviceId() + ":LOW_BATTERY";
        if (event.batteryPct() < BATTERY_LOW_THRESHOLD) {
            alertService.raiseAlertIfNotOpen(
                    event.deviceId(),
                    AlertType.LOW_BATTERY,
                    AlertSeverity.WARNING,
                    "Battery level critical: " + event.batteryPct() + "%",
                    event.lat(),
                    event.lng(),
                    openKey
            );
        } else if (event.batteryPct() >= BATTERY_CLEAR_THRESHOLD) {
            // Hysteresis: clear open alert once recharged above 20%
            alertService.resolveAlertByOpenKey(openKey, "auto-hysteresis");
        }
    }

    public void evaluateSpeed(TelemetryEvent event) {
        String openKey = event.deviceId() + ":SPEEDING";
        if (event.speedKmh() > SPEED_LIMIT_KMH) {
            alertService.raiseAlertIfNotOpen(
                    event.deviceId(),
                    AlertType.SPEEDING,
                    AlertSeverity.WARNING,
                    "Speed limit exceeded: " + Math.round(event.speedKmh()) + " km/h (limit " + (int) SPEED_LIMIT_KMH + " km/h)",
                    event.lat(),
                    event.lng(),
                    openKey
            );
        } else if (event.speedKmh() <= SPEED_CLEAR_KMH) {
            alertService.resolveAlertByOpenKey(openKey, "auto-speed-normal");
        }
    }

    public void evaluateGeofences(TelemetryEvent event) {
        List<Geofence> activeGeofences = geofenceRepository.findByActiveTrue();
        for (Geofence geofence : activeGeofences) {
            String openKey = event.deviceId() + ":GEOFENCE:" + geofence.getId();
            boolean breached = geofenceEvaluator.isBreached(geofence, event.lat(), event.lng());

            if (breached) {
                alertService.raiseAlertIfNotOpen(
                        event.deviceId(),
                        AlertType.GEOFENCE_BREACH,
                        AlertSeverity.HIGH,
                        "Geofence breached: " + geofence.getName() + " (" + geofence.getType() + ")",
                        event.lat(),
                        event.lng(),
                        openKey
                );
            } else {
                alertService.resolveAlertByOpenKey(openKey, "auto-geofence-cleared");
            }
        }
    }

    public void evaluateOfflineClear(TelemetryEvent event) {
        String openKey = event.deviceId() + ":OFFLINE";
        alertService.resolveAlertByOpenKey(openKey, "auto-heartbeat");
    }
}

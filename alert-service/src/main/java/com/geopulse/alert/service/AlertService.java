package com.geopulse.alert.service;

import com.geopulse.alert.domain.Alert;
import com.geopulse.alert.domain.AlertSeverity;
import com.geopulse.alert.domain.AlertStatus;
import com.geopulse.alert.domain.AlertType;
import com.geopulse.alert.domain.Geofence;
import com.geopulse.alert.dto.AlertDTOs.GeofenceRequest;
import com.geopulse.alert.repository.AlertRepository;
import com.geopulse.alert.repository.GeofenceRepository;
import com.geopulse.common.aspect.Audited;
import com.geopulse.common.error.ApiException;
import com.geopulse.common.event.AlertEvent;
import com.geopulse.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final GeofenceRepository geofenceRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AlertService(AlertRepository alertRepository,
                        GeofenceRepository geofenceRepository,
                        KafkaTemplate<String, Object> kafkaTemplate) {
        this.alertRepository = alertRepository;
        this.geofenceRepository = geofenceRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public Optional<Alert> raiseAlertIfNotOpen(
            String deviceId,
            AlertType type,
            AlertSeverity severity,
            String message,
            Double lat,
            Double lng,
            String openKey
    ) {
        if (openKey != null && alertRepository.existsByOpenKey(openKey)) {
            return Optional.empty();
        }

        Alert alert = Alert.builder()
                .deviceId(deviceId)
                .type(type)
                .severity(severity)
                .status(AlertStatus.OPEN)
                .message(message)
                .lat(lat)
                .lng(lng)
                .openKey(openKey)
                .build();

        alert = alertRepository.save(alert);
        publishAlertEvent(alert);
        log.info("Raised alert id={} deviceId={} type={} severity={}", alert.getId(), deviceId, type, severity);
        return Optional.of(alert);
    }

    @Transactional
    public Optional<Alert> resolveAlertByOpenKey(String openKey, String resolvedBy) {
        Optional<Alert> opt = alertRepository.findByOpenKey(openKey);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Alert alert = opt.get();
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedBy(resolvedBy != null ? resolvedBy : "system");
        alert.setResolvedAt(Instant.now());
        alert.setOpenKey(null);
        alert = alertRepository.save(alert);
        publishAlertEvent(alert);
        log.info("Auto-resolved alert id={} openKey={}", alert.getId(), openKey);
        return Optional.of(alert);
    }

    @Transactional
    @Audited(action = "ALERT_ACKNOWLEDGE", entityIdParam = "id")
    public Alert acknowledgeAlert(Long id, String acknowledgedBy) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Alert " + id));

        if (alert.getStatus() != AlertStatus.OPEN) {
            throw ApiException.badRequest("invalid-alert-state", "Alert " + id + " is not in OPEN status");
        }

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(Instant.now());
        alert = alertRepository.save(alert);
        publishAlertEvent(alert);
        log.info("Acknowledged alert id={} by {}", id, acknowledgedBy);
        return alert;
    }

    @Transactional
    @Audited(action = "ALERT_RESOLVE", entityIdParam = "id")
    public Alert resolveAlert(Long id, String resolvedBy) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Alert " + id));

        if (alert.getStatus() == AlertStatus.RESOLVED) {
            return alert;
        }

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(Instant.now());
        alert.setOpenKey(null);
        alert = alertRepository.save(alert);
        publishAlertEvent(alert);
        log.info("Resolved alert id={} by {}", id, resolvedBy);
        return alert;
    }

    @Transactional(readOnly = true)
    public Page<Alert> getAlerts(String deviceId, AlertStatus status, Pageable pageable) {
        if (deviceId != null && !deviceId.isBlank() && status != null) {
            return alertRepository.findByDeviceIdAndStatus(deviceId, status, pageable);
        } else if (deviceId != null && !deviceId.isBlank()) {
            return alertRepository.findByDeviceId(deviceId, pageable);
        } else if (status != null) {
            return alertRepository.findByStatus(status, pageable);
        }
        return alertRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Alert getAlert(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Alert " + id));
    }

    @Transactional
    @Audited(action = "GEOFENCE_CREATE")
    public Geofence createGeofence(GeofenceRequest request) {
        Geofence geofence = Geofence.builder()
                .name(request.name())
                .type(request.type())
                .centerLat(request.centerLat())
                .centerLng(request.centerLng())
                .radiusMeters(request.radiusMeters())
                .polygonGeoJson(request.polygonGeoJson())
                .active(request.active() != null ? request.active() : true)
                .build();
        return geofenceRepository.save(geofence);
    }

    @Transactional(readOnly = true)
    public List<Geofence> listGeofences() {
        return geofenceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Geofence getGeofence(Long id) {
        return geofenceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Geofence " + id));
    }

    @Transactional
    @Audited(action = "GEOFENCE_DELETE", entityIdParam = "id")
    public void deleteGeofence(Long id) {
        if (!geofenceRepository.existsById(id)) {
            throw ApiException.notFound("Geofence " + id);
        }
        geofenceRepository.deleteById(id);
    }

    private void publishAlertEvent(Alert alert) {
        AlertEvent event = new AlertEvent(
                String.valueOf(alert.getId()),
                alert.getDeviceId(),
                alert.getType().name(),
                alert.getSeverity().name(),
                alert.getMessage(),
                alert.getLat() != null ? alert.getLat() : 0.0,
                alert.getLng() != null ? alert.getLng() : 0.0,
                alert.getCreatedAt()
        );
        kafkaTemplate.send(Topics.ALERTS, alert.getDeviceId(), event);
    }
}

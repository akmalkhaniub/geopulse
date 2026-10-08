package com.geopulse.alert.service;

import com.geopulse.alert.domain.Alert;
import com.geopulse.alert.domain.AlertSeverity;
import com.geopulse.alert.domain.AlertStatus;
import com.geopulse.alert.domain.AlertType;
import com.geopulse.alert.repository.AlertRepository;
import com.geopulse.alert.repository.GeofenceRepository;
import com.geopulse.common.error.ApiException;
import com.geopulse.common.event.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private GeofenceRepository geofenceRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(alertRepository, geofenceRepository, kafkaTemplate);
    }

    @Test
    void raiseAlertIfNotOpen_savesAndPublishesWhenNew() {
        when(alertRepository.existsByOpenKey("GPS-001:SOS")).thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        Optional<Alert> result = alertService.raiseAlertIfNotOpen(
                "GPS-001", AlertType.SOS, AlertSeverity.CRITICAL, "Emergency", 37.77, -122.41, "GPS-001:SOS"
        );

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(100L);
        verify(kafkaTemplate).send(eq(Topics.ALERTS), eq("GPS-001"), any());
    }

    @Test
    void raiseAlertIfNotOpen_deduplicatesIfAlreadyOpen() {
        when(alertRepository.existsByOpenKey("GPS-001:SOS")).thenReturn(true);

        Optional<Alert> result = alertService.raiseAlertIfNotOpen(
                "GPS-001", AlertType.SOS, AlertSeverity.CRITICAL, "Emergency", 37.77, -122.41, "GPS-001:SOS"
        );

        assertThat(result).isEmpty();
        verify(alertRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void acknowledgeAlert_transitionsStatus() {
        Alert alert = Alert.builder()
                .id(1L)
                .deviceId("GPS-001")
                .type(AlertType.LOW_BATTERY)
                .severity(AlertSeverity.WARNING)
                .status(AlertStatus.OPEN)
                .message("Low battery")
                .openKey("GPS-001:LOW_BATTERY")
                .build();

        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        Alert updated = alertService.acknowledgeAlert(1L, "op-user");

        assertThat(updated.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(updated.getAcknowledgedBy()).isEqualTo("op-user");
        assertThat(updated.getAcknowledgedAt()).isNotNull();
        verify(kafkaTemplate).send(eq(Topics.ALERTS), eq("GPS-001"), any());
    }

    @Test
    void acknowledgeAlert_rejectsIfNotOpen() {
        Alert alert = Alert.builder()
                .id(1L)
                .status(AlertStatus.RESOLVED)
                .build();

        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> alertService.acknowledgeAlert(1L, "op-user"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void resolveAlert_clearsOpenKeyAndPublishes() {
        Alert alert = Alert.builder()
                .id(1L)
                .deviceId("GPS-001")
                .type(AlertType.LOW_BATTERY)
                .severity(AlertSeverity.WARNING)
                .status(AlertStatus.OPEN)
                .openKey("GPS-001:LOW_BATTERY")
                .build();

        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        Alert resolved = alertService.resolveAlert(1L, "admin-user");

        assertThat(resolved.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(resolved.getResolvedBy()).isEqualTo("admin-user");
        assertThat(resolved.getOpenKey()).isNull();
        verify(kafkaTemplate).send(eq(Topics.ALERTS), eq("GPS-001"), any());
    }
}

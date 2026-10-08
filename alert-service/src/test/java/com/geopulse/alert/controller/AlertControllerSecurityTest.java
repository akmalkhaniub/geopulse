package com.geopulse.alert.controller;

import com.geopulse.alert.config.SecurityConfig;
import com.geopulse.alert.domain.Alert;
import com.geopulse.alert.domain.AlertSeverity;
import com.geopulse.alert.domain.AlertStatus;
import com.geopulse.alert.domain.AlertType;
import com.geopulse.alert.domain.Geofence;
import com.geopulse.alert.domain.GeofenceType;
import com.geopulse.alert.service.AlertService;
import com.geopulse.common.config.GeopulseCommonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AlertController.class, GeofenceController.class})
@Import(SecurityConfig.class)
@ImportAutoConfiguration(GeopulseCommonAutoConfiguration.class)
class AlertControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AlertService alertService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsAnonymous() throws Exception {
        mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/geofences")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"viewer", "operator", "admin"})
    void allowsAuthenticatedRolesToListAlerts(String role) throws Exception {
        when(alertService.getAlerts(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/alerts").with(as(role)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsViewerFromAcknowledgingOrResolving() throws Exception {
        mvc.perform(post("/api/alerts/1/acknowledge").with(as("viewer")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/alerts/1/resolve").with(as("viewer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsOperatorToAcknowledgeAndResolve() throws Exception {
        Alert alert = Alert.builder()
                .id(1L)
                .deviceId("GPS-001")
                .type(AlertType.LOW_BATTERY)
                .severity(AlertSeverity.WARNING)
                .status(AlertStatus.ACKNOWLEDGED)
                .message("Low")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(alertService.acknowledgeAlert(eq(1L), any())).thenReturn(alert);
        when(alertService.resolveAlert(eq(1L), any())).thenReturn(alert);

        mvc.perform(post("/api/alerts/1/acknowledge").with(as("operator")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/alerts/1/resolve").with(as("operator")))
                .andExpect(status().isOk());
    }

    @Test
    void operatorCannotDeleteGeofence_adminCan() throws Exception {
        mvc.perform(delete("/api/geofences/1").with(as("operator")))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/geofences/1").with(as("admin")))
                .andExpect(status().isNoContent());

        verify(alertService).deleteGeofence(1L);
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("user-" + role)
                        .claim("preferred_username", "test-" + role)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}

package com.geopulse.telemetry.controller;

import com.geopulse.common.config.GeopulseCommonAutoConfiguration;
import com.geopulse.telemetry.config.SecurityConfig;
import com.geopulse.telemetry.dto.TelemetryDTOs.PositionRecord;
import com.geopulse.telemetry.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
@Import(SecurityConfig.class)
@ImportAutoConfiguration(GeopulseCommonAutoConfiguration.class)
class TelemetryControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TelemetryService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsAnonymous() throws Exception {
        mvc.perform(get("/api/telemetry/GPS-001/latest")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"viewer", "operator", "admin"})
    void allowsAuthenticatedRolesToGetLatest(String role) throws Exception {
        PositionRecord record = new PositionRecord(1L, "msg-1", "GPS-001", 37.77, -122.41,
                50.0, 90.0, 80, false, Instant.now(), Instant.now());
        when(service.getLatest("GPS-001")).thenReturn(record);

        mvc.perform(get("/api/telemetry/GPS-001/latest").with(as(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"viewer", "operator", "admin"})
    void allowsAuthenticatedRolesToGetHistory(String role) throws Exception {
        when(service.getHistory(eq("GPS-001"), any(), any(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/telemetry/GPS-001/history").with(as(role)))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("user-" + role)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}

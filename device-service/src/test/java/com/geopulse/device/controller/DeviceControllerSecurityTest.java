package com.geopulse.device.controller;

import com.geopulse.common.config.GeopulseCommonAutoConfiguration;
import com.geopulse.device.config.SecurityConfig;
import com.geopulse.device.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
@Import(SecurityConfig.class)
@ImportAutoConfiguration(GeopulseCommonAutoConfiguration.class)
class DeviceControllerSecurityTest {

    private static final String ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DeviceService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(service.list(any(), any(), any())).thenReturn(Page.empty());
    }

    @Test
    void rejectsAnonymous() throws Exception {
        mvc.perform(get("/api/devices")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"viewer", "operator", "admin"})
    void allowsViewerOperatorAdminToListDevices(String role) throws Exception {
        mvc.perform(get("/api/devices").with(as(role))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"viewer,false", "operator,false", "admin,true"})
    void createDeviceRoleCheck(String role, boolean allowed) throws Exception {
        String body = "{\"externalId\":\"GPS-001\",\"name\":\"Van\",\"type\":\"VEHICLE\"}";
        int status = mvc.perform(post("/api/devices").with(as(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();

        if (allowed) {
            assertThat(status).isNotIn(401, 403);
        } else {
            assertThat(status).isEqualTo(403);
        }
    }

    @ParameterizedTest
    @CsvSource({"viewer,false", "operator,false", "admin,true"})
    void deleteDeviceRoleCheck(String role, boolean allowed) throws Exception {
        int status = mvc.perform(delete("/api/devices/" + ID).with(as(role)))
                .andReturn().getResponse().getStatus();

        if (allowed) {
            assertThat(status).isNotIn(401, 403);
        } else {
            assertThat(status).isEqualTo(403);
        }
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("user-" + role)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}

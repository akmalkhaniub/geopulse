package com.geopulse.device.config;

import com.geopulse.common.security.GeopulseHttpSecurity;
import com.geopulse.common.security.KeycloakJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, KeycloakJwtAuthenticationConverter converter)
            throws Exception {
        return GeopulseHttpSecurity.configureResourceServer(http, converter).build();
    }
}

package com.geopulse.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geopulse.security")
public record GeopulseSecurityProperties(
        String issuerUri,
        String jwkSetUri,
        String audience
) {
    public GeopulseSecurityProperties {
        if (audience == null || audience.isBlank()) {
            audience = "fleet-api";
        }
    }
}

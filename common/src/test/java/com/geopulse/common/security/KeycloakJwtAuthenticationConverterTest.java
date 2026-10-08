package com.geopulse.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void mapsRealmRolesAndScope() {
        Jwt jwt = Jwt.withTokenValue("mock")
                .header("alg", "RS256")
                .subject("user-uuid-123")
                .claim("realm_access", Map.of("roles", List.of("operator", "viewer")))
                .claim("scope", "email profile")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        var auth = converter.convert(jwt);

        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user-uuid-123");
        Collection<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authorities).contains("ROLE_operator", "ROLE_viewer", "SCOPE_email", "SCOPE_profile");
    }
}

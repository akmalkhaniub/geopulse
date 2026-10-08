package com.geopulse.common.config;

import com.geopulse.common.aspect.AuditAspect;
import com.geopulse.common.error.ProblemExceptionHandler;
import com.geopulse.common.security.GeopulseSecurityProperties;
import com.geopulse.common.security.KeycloakJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@AutoConfiguration
@EnableConfigurationProperties(GeopulseSecurityProperties.class)
public class GeopulseCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuditAspect auditAspect(@Value("${spring.application.name:geopulse-service}") String serviceName) {
        return new AuditAspect(serviceName);
    }

    @Bean
    @ConditionalOnMissingBean
    KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemExceptionHandler problemExceptionHandler() {
        return new ProblemExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty("geopulse.security.jwk-set-uri")
    JwtDecoder jwtDecoder(GeopulseSecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.issuerUri()),
                audienceValidator(props.audience())
        ));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience missing: " + audience, null));
    }
}

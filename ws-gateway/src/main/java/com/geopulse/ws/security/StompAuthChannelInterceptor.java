package com.geopulse.ws.security;

import com.geopulse.common.security.KeycloakJwtAuthenticationConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtDecoder jwtDecoder;
    private final KeycloakJwtAuthenticationConverter authenticationConverter;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder,
                                        KeycloakJwtAuthenticationConverter authenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeader = accessor.getNativeHeader("Authorization");
            if (authHeader == null || authHeader.isEmpty()) {
                authHeader = accessor.getNativeHeader("authorization");
            }

            if (authHeader == null || authHeader.isEmpty() || !authHeader.getFirst().startsWith("Bearer ")) {
                log.warn("STOMP CONNECT rejected: missing or malformed Bearer token");
                throw new BadCredentialsException("Missing or malformed Authorization header in STOMP CONNECT");
            }

            String token = authHeader.getFirst().substring(7).trim();
            try {
                Jwt jwt = jwtDecoder.decode(token);
                Authentication auth = authenticationConverter.convert(jwt);
                accessor.setUser(auth);
                log.debug("STOMP CONNECT authenticated user={}", auth != null ? auth.getName() : "null");
            } catch (Exception e) {
                log.warn("STOMP CONNECT token validation failed: {}", e.getMessage());
                throw new BadCredentialsException("Invalid token: " + e.getMessage(), e);
            }
        }
        return message;
    }
}

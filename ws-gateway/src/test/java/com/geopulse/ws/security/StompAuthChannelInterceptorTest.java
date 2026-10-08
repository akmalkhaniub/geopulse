package com.geopulse.ws.security;

import com.geopulse.common.security.KeycloakJwtAuthenticationConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private KeycloakJwtAuthenticationConverter converter;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtDecoder, converter);
    }

    @Test
    void preSend_connectWithValidToken_authenticatesUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Jwt jwt = new Jwt("valid-token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "none"), Map.of("sub", "user-123"));
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        UsernamePasswordAuthenticationToken expectedAuth = new UsernamePasswordAuthenticationToken(
                "user-123", null, List.of(new SimpleGrantedAuthority("ROLE_viewer")));
        when(converter.convert(jwt)).thenReturn(expectedAuth);

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
        assertThat(result).isNotNull();

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isEqualTo(expectedAuth);
    }

    @Test
    void preSend_connectWithoutToken_throwsBadCredentialsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or malformed Authorization header");
    }

    @Test
    void preSend_connectWithInvalidToken_throwsBadCredentialsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtDecoder.decode("bad-token")).thenThrow(new BadJwtException("Invalid signature"));

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid token");
    }

    @Test
    void preSend_nonConnectCommand_bypassesAuthentication() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
        assertThat(result).isNotNull();
    }
}

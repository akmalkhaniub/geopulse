package com.geopulse.ingestion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(redis);
    }

    @Test
    void detectsDuplicate() {
        when(redis.hasKey("telemetry:dedupe:msg-1")).thenReturn(true);
        assertThat(service.isDuplicate("msg-1")).isTrue();
    }

    @Test
    void allowsFreshMessage() {
        when(redis.hasKey("telemetry:dedupe:msg-2")).thenReturn(false);
        assertThat(service.isDuplicate("msg-2")).isFalse();
    }

    @Test
    void marksPublishedWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service.markPublished("msg-3");
        verify(valueOps).set(eq("telemetry:dedupe:msg-3"), eq("1"), eq(Duration.ofSeconds(300)));
    }
}

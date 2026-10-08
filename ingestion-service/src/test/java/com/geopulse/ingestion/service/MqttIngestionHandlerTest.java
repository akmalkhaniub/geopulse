package com.geopulse.ingestion.service;

import com.geopulse.common.event.Topics;
import com.geopulse.ingestion.validation.TelemetryValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttIngestionHandlerTest {

    private final Instant now = Instant.parse("2026-10-08T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private IdempotencyService idempotency;

    @Mock
    private KafkaTemplate<String, Object> kafka;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private SetOperations<String, String> setOps;

    private MqttIngestionHandler handler;

    @BeforeEach
    void setUp() {
        TelemetryValidator validator = new TelemetryValidator(clock);
        handler = new MqttIngestionHandler(validator, idempotency, kafka, redis, json, clock);
    }

    @Test
    void publishesValidTelemetryAndMarksDedupe() {
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(MqttIngestionHandler.REDIS_REGISTERED_SET, "GPS-001")).thenReturn(true);
        when(idempotency.isDuplicate("msg-100")).thenReturn(false);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafka.send(eq(Topics.TELEMETRY_RAW), eq("GPS-001"), any())).thenReturn(future);

        String payload = """
                {
                  "deviceId": "GPS-001",
                  "lat": 37.77,
                  "lng": -122.41,
                  "speedKmh": 50.0,
                  "headingDeg": 120.0,
                  "batteryPct": 80,
                  "sos": false,
                  "recordedAt": "2026-10-08T11:59:50Z",
                  "messageId": "msg-100"
                }
                """;

        handler.handleTelemetry("GPS-001", payload, false);

        verify(kafka).send(eq(Topics.TELEMETRY_RAW), eq("GPS-001"), any());
        verify(idempotency).markPublished("msg-100");
    }

    @Test
    void dropsDuplicateTelemetry() {
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(MqttIngestionHandler.REDIS_REGISTERED_SET, "GPS-001")).thenReturn(true);
        when(idempotency.isDuplicate("msg-dup")).thenReturn(true);

        String payload = """
                {
                  "deviceId": "GPS-001",
                  "lat": 37.77,
                  "lng": -122.41,
                  "speedKmh": 50.0,
                  "headingDeg": 120.0,
                  "batteryPct": 80,
                  "sos": false,
                  "recordedAt": "2026-10-08T11:59:50Z",
                  "messageId": "msg-dup"
                }
                """;

        handler.handleTelemetry("GPS-001", payload, false);

        verify(kafka, never()).send(any(), any(), any());
        verify(idempotency, never()).markPublished("msg-dup");
    }
}

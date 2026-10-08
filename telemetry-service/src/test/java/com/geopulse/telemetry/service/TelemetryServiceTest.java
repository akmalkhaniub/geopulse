package com.geopulse.telemetry.service;

import com.geopulse.common.event.TelemetryEvent;
import com.geopulse.common.event.Topics;
import com.geopulse.telemetry.domain.PositionHistory;
import com.geopulse.telemetry.repository.PositionHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    private final Instant now = Instant.parse("2026-10-08T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private PositionHistoryRepository repository;

    @Mock
    private KafkaTemplate<String, Object> kafka;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ZSetOperations<String, String> zsetOps;

    private TelemetryService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryService(repository, kafka, redis, json, clock);
    }

    @Test
    void processesAndCachesNewTelemetry() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForZSet()).thenReturn(zsetOps);
        when(repository.existsByMessageId("msg-1")).thenReturn(false);

        TelemetryEvent event = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 60.0, 90.0, 80, false, now, "msg-1"
        );

        service.onRawTelemetry(event);

        verify(repository).save(any(PositionHistory.class));
        verify(kafka).send(eq(Topics.TELEMETRY_PROCESSED), eq("GPS-001"), eq(event));
        verify(valueOps).set(eq(TelemetryService.KEY_LATEST_PREFIX + "GPS-001"), any(), eq(Duration.ofHours(24)));
        verify(zsetOps).add(eq(TelemetryService.KEY_LASTSEEN), eq("GPS-001"), eq((double) now.toEpochMilli()));
    }

    @Test
    void skipsDuplicateMessageId() {
        when(repository.existsByMessageId("msg-dup")).thenReturn(true);

        TelemetryEvent event = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 60.0, 90.0, 80, false, now, "msg-dup"
        );

        service.onRawTelemetry(event);

        verify(repository, never()).save(any());
        verify(kafka, never()).send(any(), any(), any());
    }

    @Test
    void olderRecordedAtDoesNotOverwriteNewerInRedis() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForZSet()).thenReturn(zsetOps);
        when(repository.existsByMessageId("msg-old")).thenReturn(false);

        TelemetryEvent newerEvent = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 60.0, 90.0, 80, false, now, "msg-newer"
        );
        when(valueOps.get(TelemetryService.KEY_LATEST_PREFIX + "GPS-001"))
                .thenReturn(json.writeValueAsString(newerEvent));

        // Event timestamp is 1 hour before cached value
        TelemetryEvent olderEvent = new TelemetryEvent(
                "GPS-001", 37.76, -122.40, 50.0, 80.0, 75, false, now.minusSeconds(3600), "msg-old"
        );

        service.onRawTelemetry(olderEvent);

        verify(repository).save(any(PositionHistory.class));
        verify(kafka).send(eq(Topics.TELEMETRY_PROCESSED), eq("GPS-001"), eq(olderEvent));
        verify(valueOps, never()).set(eq(TelemetryService.KEY_LATEST_PREFIX + "GPS-001"), any(String.class), any(Duration.class));
    }
}

package com.geopulse.telemetry.service;

import com.geopulse.common.error.ApiException;
import com.geopulse.common.event.TelemetryEvent;
import com.geopulse.common.event.Topics;
import com.geopulse.telemetry.domain.PositionHistory;
import com.geopulse.telemetry.dto.TelemetryDTOs.PositionRecord;
import com.geopulse.telemetry.repository.PositionHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
public class TelemetryService {

    public static final String KEY_LATEST_PREFIX = "device:latest:";
    public static final String KEY_LASTSEEN = "device:lastseen";
    private static final Duration MAX_HISTORY_RANGE = Duration.ofDays(7);

    private final PositionHistoryRepository repository;
    private final KafkaTemplate<String, Object> kafka;
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Clock clock;

    public TelemetryService(PositionHistoryRepository repository, KafkaTemplate<String, Object> kafka,
                            StringRedisTemplate redis, JsonMapper json, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.redis = redis;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    @KafkaListener(topics = Topics.TELEMETRY_RAW, groupId = "telemetry-service")
    public void onRawTelemetry(TelemetryEvent event) {
        if (event == null || event.messageId() == null) {
            return;
        }

        // Idempotency: duplicate check
        if (repository.existsByMessageId(event.messageId())) {
            log.debug("Telemetry event messageId {} already persisted; skipping redelivery", event.messageId());
            return;
        }

        PositionHistory p = new PositionHistory();
        p.setMessageId(event.messageId());
        p.setDeviceId(event.deviceId());
        p.setLat(event.lat());
        p.setLng(event.lng());
        p.setSpeedKmh(event.speedKmh());
        p.setHeadingDeg(event.headingDeg());
        p.setBatteryPct(event.batteryPct());
        p.setSos(event.sos());
        p.setRecordedAt(event.recordedAt());
        p.setIngestedAt(clock.instant());

        repository.save(p);

        // Update Redis caches
        updateRedisCaches(event);

        // Publish to telemetry.processed for alert-service, device-service, and ws-gateway
        kafka.send(Topics.TELEMETRY_PROCESSED, event.deviceId(), event);
    }

    private void updateRedisCaches(TelemetryEvent event) {
        try {
            String latestKey = KEY_LATEST_PREFIX + event.deviceId();
            String existingJson = redis.opsForValue().get(latestKey);
            boolean shouldUpdate = true;
            if (existingJson != null) {
                try {
                    TelemetryEvent prev = json.readValue(existingJson, TelemetryEvent.class);
                    if (prev.recordedAt() != null && event.recordedAt().isBefore(prev.recordedAt())) {
                        shouldUpdate = false;
                    }
                } catch (Exception ignored) {
                }
            }

            if (shouldUpdate) {
                redis.opsForValue().set(latestKey, json.writeValueAsString(event), Duration.ofHours(24));
            }

            // Update sorted set for offline scanner
            redis.opsForZSet().add(KEY_LASTSEEN, event.deviceId(), event.recordedAt().toEpochMilli());
        } catch (Exception e) {
            log.warn("Redis caching error (failing open): {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PositionRecord getLatest(String deviceId) {
        try {
            String cached = redis.opsForValue().get(KEY_LATEST_PREFIX + deviceId);
            if (cached != null) {
                TelemetryEvent event = json.readValue(cached, TelemetryEvent.class);
                return new PositionRecord(null, event.messageId(), event.deviceId(), event.lat(), event.lng(),
                        event.speedKmh(), event.headingDeg(), event.batteryPct(), event.sos(), event.recordedAt(), null);
            }
        } catch (Exception e) {
            log.debug("Redis cache miss/error for {}: {}", deviceId, e.getMessage());
        }

        return repository.findTopByDeviceIdOrderByRecordedAtDesc(deviceId)
                .map(PositionRecord::from)
                .orElseThrow(() -> ApiException.notFound("Telemetry for device " + deviceId));
    }

    @Transactional(readOnly = true)
    public Page<PositionRecord> getHistory(String deviceId, Instant from, Instant to, Pageable pageable) {
        Instant start = from != null ? from : clock.instant().minus(Duration.ofHours(24));
        Instant end = to != null ? to : clock.instant();

        if (start.isAfter(end)) {
            throw ApiException.badRequest("invalid-range", "'from' timestamp must not be after 'to'");
        }

        if (Duration.between(start, end).compareTo(MAX_HISTORY_RANGE) > 0) {
            throw ApiException.badRequest("range-exceeded", "Maximum history range query is 7 days");
        }

        return repository.findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtDesc(deviceId, start, end, pageable)
                .map(PositionRecord::from);
    }
}

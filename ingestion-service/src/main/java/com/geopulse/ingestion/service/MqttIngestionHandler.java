package com.geopulse.ingestion.service;

import com.geopulse.common.event.DeviceStatusEvent;
import com.geopulse.common.event.TelemetryEvent;
import com.geopulse.common.event.Topics;
import com.geopulse.ingestion.validation.TelemetryValidator;
import com.geopulse.ingestion.validation.TelemetryValidator.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class MqttIngestionHandler {

    public static final String REDIS_REGISTERED_SET = "devices:registered";

    private final TelemetryValidator validator;
    private final IdempotencyService idempotency;
    private final KafkaTemplate<String, Object> kafka;
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Clock clock;

    public MqttIngestionHandler(TelemetryValidator validator, IdempotencyService idempotency,
                                KafkaTemplate<String, Object> kafka, StringRedisTemplate redis,
                                JsonMapper json, Clock clock) {
        this.validator = validator;
        this.idempotency = idempotency;
        this.kafka = kafka;
        this.redis = redis;
        this.json = json;
        this.clock = clock;
    }

    public void handleTelemetry(String topicDeviceId, String payloadJson, boolean isSosTopic) {
        try {
            TelemetryEvent event = json.readValue(payloadJson, TelemetryEvent.class);
            if (isSosTopic) {
                event = new TelemetryEvent(event.deviceId(), event.lat(), event.lng(), event.speedKmh(),
                        event.headingDeg(), event.batteryPct(), true, event.recordedAt(), event.messageId());
            }

            Result valResult = validator.validate(topicDeviceId, event);
            if (!valResult.valid()) {
                log.warn("Dropped invalid telemetry for {}: {}", topicDeviceId, valResult.reason());
                return;
            }

            try {
                Boolean registered = redis.opsForSet().isMember(REDIS_REGISTERED_SET, event.deviceId());
                if (registered != null && !registered) {
                    log.warn("Dropped telemetry from unregistered device {}", event.deviceId());
                    return;
                }
            } catch (Exception e) {
                log.warn("Redis registration lookup failed (failing open): {}", e.getMessage());
            }

            if (idempotency.isDuplicate(event.messageId())) {
                log.debug("Dropped duplicate telemetry messageId: {}", event.messageId());
                return;
            }

            kafka.send(Topics.TELEMETRY_RAW, event.deviceId(), event).get();

            idempotency.markPublished(event.messageId());
        } catch (Exception e) {
            log.error("Failed to process telemetry payload for {}: {}", topicDeviceId, e.getMessage());
        }
    }

    public void handleStatus(String topicDeviceId, String payloadJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = json.readValue(payloadJson, Map.class);
            Object statusObj = map.get("status");
            String status = statusObj != null ? statusObj.toString().toUpperCase() : "OFFLINE";
            Object atObj = map.get("at");
            Instant at = atObj != null ? Instant.parse(atObj.toString()) : clock.instant();

            DeviceStatusEvent statusEvent = new DeviceStatusEvent(
                    topicDeviceId,
                    status,
                    DeviceStatusEvent.SOURCE_LWT,
                    at
            );

            kafka.send(Topics.DEVICE_STATUS, topicDeviceId, statusEvent);
        } catch (Exception e) {
            log.error("Failed to process status payload for {}: {}", topicDeviceId, e.getMessage());
        }
    }
}

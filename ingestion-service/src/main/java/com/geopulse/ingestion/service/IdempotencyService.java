package com.geopulse.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Mark-after-publish deduplication service backed by Redis.
 * Fails open if Redis is unavailable so messages are never silently dropped.
 */
@Slf4j
@Service
public class IdempotencyService {

    public static final String PREFIX = "telemetry:dedupe:";
    private static final Duration TTL = Duration.ofSeconds(300);

    private final StringRedisTemplate redis;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isDuplicate(String messageId) {
        try {
            Boolean exists = redis.hasKey(PREFIX + messageId);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Redis dedupe check failed (failing open): {}", e.getMessage());
            return false;
        }
    }

    public void markPublished(String messageId) {
        try {
            redis.opsForValue().set(PREFIX + messageId, "1", TTL);
        } catch (Exception e) {
            log.warn("Redis dedupe mark failed: {}", e.getMessage());
        }
    }
}

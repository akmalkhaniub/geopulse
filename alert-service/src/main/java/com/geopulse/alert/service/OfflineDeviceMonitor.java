package com.geopulse.alert.service;

import com.geopulse.alert.domain.AlertSeverity;
import com.geopulse.alert.domain.AlertType;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
public class OfflineDeviceMonitor {

    private static final Logger log = LoggerFactory.getLogger(OfflineDeviceMonitor.class);
    private static final String KEY_LASTSEEN = "device:lastseen";

    private final StringRedisTemplate redisTemplate;
    private final AlertService alertService;
    private final long offlineThresholdSeconds;

    public OfflineDeviceMonitor(
            StringRedisTemplate redisTemplate,
            AlertService alertService,
            @Value("${geopulse.alerts.offline-threshold-seconds:600}") long offlineThresholdSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.alertService = alertService;
        this.offlineThresholdSeconds = offlineThresholdSeconds;
    }

    @Scheduled(fixedDelayString = "${geopulse.alerts.offline-check-interval-ms:60000}", initialDelay = 15000)
    @SchedulerLock(name = "OfflineDeviceCheck", lockAtMostFor = "55s", lockAtLeastFor = "10s")
    public void scanOfflineDevices() {
        long cutoffEpochSeconds = Instant.now().minusSeconds(offlineThresholdSeconds).getEpochSecond();

        try {
            Set<ZSetOperations.TypedTuple<String>> staleDevices =
                    redisTemplate.opsForZSet().rangeByScoreWithScores(KEY_LASTSEEN, 0, cutoffEpochSeconds);

            if (staleDevices == null || staleDevices.isEmpty()) {
                return;
            }

            for (ZSetOperations.TypedTuple<String> entry : staleDevices) {
                String deviceId = entry.getValue();
                if (deviceId != null && !deviceId.isBlank()) {
                    String openKey = deviceId + ":OFFLINE";
                    alertService.raiseAlertIfNotOpen(
                            deviceId,
                            AlertType.OFFLINE,
                            AlertSeverity.CRITICAL,
                            "Device has been offline for over " + (offlineThresholdSeconds / 60) + " minutes",
                            null,
                            null,
                            openKey
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Offline device scan encountered an error: {}", e.getMessage());
        }
    }
}

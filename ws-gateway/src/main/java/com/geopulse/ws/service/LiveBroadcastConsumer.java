package com.geopulse.ws.service;

import com.geopulse.common.event.AlertEvent;
import com.geopulse.common.event.DeviceStatusEvent;
import com.geopulse.common.event.TelemetryEvent;
import com.geopulse.common.event.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LiveBroadcastConsumer {

    private static final Logger log = LoggerFactory.getLogger(LiveBroadcastConsumer.class);

    private final SimpMessagingTemplate messagingTemplate;

    public LiveBroadcastConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = Topics.TELEMETRY_PROCESSED, groupId = "ws-gateway-telemetry")
    public void onTelemetry(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/positions", event);
        if (event.deviceId() != null) {
            messagingTemplate.convertAndSend("/topic/positions/" + event.deviceId(), event);
        }
    }

    @KafkaListener(topics = Topics.ALERTS, groupId = "ws-gateway-alerts")
    public void onAlert(AlertEvent event) {
        if (event == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/alerts", event);
        if (event.deviceId() != null) {
            messagingTemplate.convertAndSend("/topic/alerts/" + event.deviceId(), event);
        }
    }

    @KafkaListener(topics = Topics.DEVICE_STATUS, groupId = "ws-gateway-device-status")
    public void onDeviceStatus(DeviceStatusEvent event) {
        if (event == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/device-status", event);
        if (event.deviceId() != null) {
            messagingTemplate.convertAndSend("/topic/device-status/" + event.deviceId(), event);
        }
    }
}

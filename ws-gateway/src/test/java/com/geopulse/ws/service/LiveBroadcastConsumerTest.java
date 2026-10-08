package com.geopulse.ws.service;

import com.geopulse.common.event.AlertEvent;
import com.geopulse.common.event.DeviceStatusEvent;
import com.geopulse.common.event.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveBroadcastConsumerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private LiveBroadcastConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LiveBroadcastConsumer(messagingTemplate);
    }

    @Test
    void onTelemetry_broadcastsToGlobalAndDeviceTopics() {
        TelemetryEvent event = new TelemetryEvent(
                "GPS-001", 37.77, -122.41, 50.0, 90.0, 80, false, Instant.now(), "msg-1"
        );

        consumer.onTelemetry(event);

        verify(messagingTemplate).convertAndSend("/topic/positions", event);
        verify(messagingTemplate).convertAndSend("/topic/positions/GPS-001", event);
    }

    @Test
    void onAlert_broadcastsToGlobalAndDeviceTopics() {
        AlertEvent event = new AlertEvent(
                "100", "GPS-001", "SOS", "CRITICAL", "SOS triggered", 37.77, -122.41, Instant.now()
        );

        consumer.onAlert(event);

        verify(messagingTemplate).convertAndSend("/topic/alerts", event);
        verify(messagingTemplate).convertAndSend("/topic/alerts/GPS-001", event);
    }

    @Test
    void onDeviceStatus_broadcastsToGlobalAndDeviceTopics() {
        DeviceStatusEvent event = new DeviceStatusEvent(
                "GPS-001", "ONLINE", "Cellular active", Instant.now()
        );

        consumer.onDeviceStatus(event);

        verify(messagingTemplate).convertAndSend("/topic/device-status", event);
        verify(messagingTemplate).convertAndSend("/topic/device-status/GPS-001", event);
    }
}

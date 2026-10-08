package com.geopulse.ingestion.mqtt;

import com.geopulse.ingestion.service.MqttIngestionHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MqttSubscriber implements MqttCallbackExtended {

    private final String brokerUrl;
    private final String clientId;
    private final boolean enabled;
    private final MqttIngestionHandler handler;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private MqttClient client;

    public MqttSubscriber(
            @Value("${geopulse.mqtt.broker-url:tcp://localhost:1883}") String brokerUrl,
            @Value("${geopulse.mqtt.client-id:ingestion-service}") String clientId,
            @Value("${geopulse.mqtt.enabled:true}") boolean enabled,
            MqttIngestionHandler handler
    ) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.enabled = enabled;
        this.handler = handler;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("MQTT subscriber disabled by configuration");
            return;
        }

        scheduler.scheduleWithFixedDelay(this::connectSafe, 0, 10, TimeUnit.SECONDS);
    }

    private synchronized void connectSafe() {
        try {
            if (client != null && client.isConnected()) {
                return;
            }
            if (client == null) {
                client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                client.setCallback(this);
            }
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            log.info("Connecting to MQTT broker at {}", brokerUrl);
            client.connect(options);
            subscribeTopics();
            log.info("Connected to MQTT broker and subscribed to device topics");
        } catch (Exception e) {
            log.warn("MQTT broker unavailable at {} (will retry): {}", brokerUrl, e.getMessage());
        }
    }

    private void subscribeTopics() throws Exception {
        client.subscribe("devices/+/telemetry", 1);
        client.subscribe("devices/+/sos", 1);
        client.subscribe("devices/+/status", 1);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT connection established (reconnect={}) to {}", reconnect, serverURI);
        try {
            subscribeTopics();
        } catch (Exception e) {
            log.error("Failed to resubscribe after reconnect: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        String[] parts = topic.split("/");
        if (parts.length < 3) {
            return;
        }
        String deviceId = parts[1];
        String subtopic = parts[2];

        switch (subtopic) {
            case "telemetry" -> handler.handleTelemetry(deviceId, payload, false);
            case "sos" -> handler.handleTelemetry(deviceId, payload, true);
            case "status" -> handler.handleStatus(deviceId, payload);
            default -> log.debug("Unknown subtopic: {}", subtopic);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            } catch (Exception e) {
                log.warn("Error closing MQTT client: {}", e.getMessage());
            }
        }
    }
}

package com.geopulse.telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
        name = "position_history",
        indexes = {
                @Index(name = "idx_pos_device_recorded", columnList = "device_id, recorded_at")
        }
)
public class PositionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 64)
    private String messageId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(name = "speed_kmh", nullable = false)
    private Double speedKmh;

    @Column(name = "heading_deg", nullable = false)
    private Double headingDeg;

    @Column(name = "battery_pct", nullable = false)
    private Integer batteryPct;

    @Column(nullable = false)
    private Boolean sos;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;
}

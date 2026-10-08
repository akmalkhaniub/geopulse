package com.geopulse.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "device")
public class Device {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConnectivityStatus connectivity;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "owner_member_id", length = 64)
    private String ownerMemberId;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_lat")
    private Double lastLat;

    @Column(name = "last_lng")
    private Double lastLng;

    @Column(name = "last_battery_pct")
    private Integer lastBatteryPct;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = DeviceStatus.ACTIVE;
        }
        if (connectivity == null) {
            connectivity = ConnectivityStatus.OFFLINE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

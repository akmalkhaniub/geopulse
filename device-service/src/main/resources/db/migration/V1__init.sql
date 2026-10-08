CREATE TABLE device (
    id BINARY(16) NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    connectivity VARCHAR(32) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    owner_member_id VARCHAR(64) NULL,
    last_seen_at TIMESTAMP(6) NULL,
    last_lat DOUBLE NULL,
    last_lng DOUBLE NULL,
    last_battery_pct INT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_device_external_id UNIQUE (external_id)
);

CREATE INDEX idx_device_status ON device (status);
CREATE INDEX idx_device_owner ON device (owner_member_id);
CREATE INDEX idx_device_deleted ON device (deleted);

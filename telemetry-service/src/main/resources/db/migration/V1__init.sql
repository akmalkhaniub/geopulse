CREATE TABLE position_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    lat DOUBLE NOT NULL,
    lng DOUBLE NOT NULL,
    speed_kmh DOUBLE NOT NULL,
    heading_deg DOUBLE NOT NULL,
    battery_pct INT NOT NULL,
    sos BOOLEAN NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    ingested_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pos_message_id UNIQUE (message_id)
);

CREATE INDEX idx_pos_device_recorded ON position_history (device_id, recorded_at);

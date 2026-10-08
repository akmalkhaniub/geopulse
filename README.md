# GeoPulse — Real-Time Fleet & Asset Tracking Platform

> **High-throughput IoT telemetry ingestion, real-time geofencing, and event-driven fleet monitoring platform.**

Architecture Specification: [`SPEC.md`](SPEC.md)

---

## 🏗️ Architecture Overview

GeoPulse tracks GPS hardware devices and field assets via MQTT / AWS IoT Core into an event-driven Spring Boot microservice pipeline, streaming real-time positions, geofence breaches, SOS signals, and device health to a live dashboard.

```
GPS Devices ──MQTT/LWT──▶ ingestion-service ──telemetry.raw──▶ telemetry-service
  (Hardware / Sim)              │ (Redis dedupe)                     │ (MySQL history)
                                ▼                                    ▼
                         device.status                       telemetry.processed
                                │                                ├──▶ alert-service (geofences & SOS)
                                ▼                                └──▶ ws-gateway ──WebSocket──▶ Dashboard
                         device-service                                   │                      (React+Leaflet)
                           (Registry)                             alerts ─┘
```

---

## 🧩 Microservices & Port Map

| Service | Port | Description & Responsibilities |
|---|---|---|
| **`device-service`** | `8081` | Hardware registry, device lifecycle (`ACTIVE`, `INACTIVE`, `LOST`), and denormalized position cache. |
| **`ingestion-service`** | `8082` | MQTT / AWS IoT intake, schema validation, LWT connection tracking, and Redis deduplication. |
| **`telemetry-service`** | `8083` | Idempotent position history persistence (`position_history`), point-in-time and bounding-box queries. |
| **`alert-service`** | `8084` | Geofence polygon breach detection, SOS trigger handling, battery alerts, and offline timeouts. |
| **`ws-gateway`** | `8085` | High-concurrency WebSocket broadcast gateway pushing updates directly to connected browser clients. |
| **`api-gateway`** | `8080` | Spring Cloud Gateway, rate limiting, and Keycloak JWT token validation / routing. |
| **`dashboard`** | `3000` | Real-time operations map (React 18 + TypeScript + Vite + Leaflet) with live breadcrumb trails. |

### Supporting Infrastructure (Docker Compose)
- **Apache Kafka 4.x (KRaft Mode)** — Distributed log broker (`9092`), ZooKeeper-free.
- **Redis 7** — Ingestion deduplication cache and ephemeral device state (`6379`).
- **MySQL 8** — Relational persistence for registries, positions, and alerts (`3306`).
- **Eclipse Mosquitto** — Local MQTT message broker (`1883`).
- **Keycloak 26.x** — OIDC / OAuth2 identity and access management (`8180`).

---

## 💡 Key Architectural Design Decisions

1. **Mark-After-Publish Deduplication**:
   - `ingestion-service` checks `EXISTS telemetry:dedupe:{messageId}`, publishes to Kafka with `acks=all`, and only sets the Redis dedupe key after broker acknowledgment (`SET telemetry:dedupe:{messageId} 1 NX EX 300`).
   - A transient crash between Kafka and Redis yields an idempotent downstream delivery, never a dropped event.

2. **State-Transition Alerting (No Alert Flooding)**:
   - Rules trigger exclusively on **state transitions** (outside $\to$ inside geofence, battery crossing below $15\%$, SOS activated), not on every periodic GPS ping.
   - Deduped via unique active constraint `open_key = deviceId|type|geofenceId`.

3. **Strict GeoJSON Coordinate Ordering**:
   - Adheres to RFC 7946 GeoJSON format `[longitude, latitude]` for polygon definitions, avoiding common latitude/longitude axis inversion defects.

4. **MQTT Last Will & Testament (LWT)**:
   - Hardware disconnects are detected immediately via broker-retained LWT payloads on `devices/{id}/status`, publishing an `OFFLINE` status event without waiting for timeouts.

5. **AOP Compliance Auditing**:
   - Public mutations across services are intercepted via `@Audited` aspect, recording actors and execution duration while keeping raw payloads and PII out of compliance logs.

---

## 📖 Implementation Guide

The full specification, schema definitions, Kafka topic layouts, and phased build sequence are documented in [`SPEC.md`](SPEC.md).

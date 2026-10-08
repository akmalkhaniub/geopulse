# Full Spec-Driven Spec — GeoPulse: Real-Time Fleet & Asset Tracking Platform
### Distributed IoT & Telemetry Architecture · Spring Boot 4.x / Java 21 / MQTT / Kafka / Redis

> HOW TO USE: build in the PHASE order at the end. Each service, model, API, and test
> is specified with high precision as the single source of truth.

---

## 0. SYSTEM SUMMARY

A platform tracking GPS devices via MQTT → AWS IoT Core → Spring Boot microservices,
with real-time dashboard, geofence/offline/SOS alerting, Keycloak auth, and Spring
AOP audit. Polyglot-free (all backend Java/Spring Boot) to keep it focused.

**Services (ports):**
- `device-service` (8081) — device registry
- `ingestion-service` (8082) — MQTT/IoT intake → Kafka
- `telemetry-service` (8083) — position storage + query
- `alert-service` (8084) — rule evaluation + alerts
- `ws-gateway` (8085) — WebSocket push to dashboard
- `api-gateway` (8080) — Spring Cloud Gateway, auth
- `dashboard` (3000) — React + TypeScript

**Infra (docker-compose):** MySQL, Redis, Kafka in **KRaft mode** (Kafka 4.x removed
ZooKeeper; Redpanda is an acceptable drop-in), Keycloak (host port **8180**, see §7),
(optional) LocalStack for AWS IoT/S3/DynamoDB emulation, Mosquitto (local MQTT broker
alternative to AWS IoT for offline dev).

**Host port map (no collisions):** gateway 8080 · services 8081–8085 · Keycloak 8180 ·
MySQL 3306 · Redis 6379 · Kafka 9092 · Mosquitto 1883 · LocalStack 4566 · dashboard 3000.

**Identifier convention (used everywhere):** `deviceId` in events, Redis keys, telemetry
and alert APIs = `Device.externalId` (hardware id). `Device.id` (UUID) is used ONLY in
device-service CRUD paths `/api/devices/{id}`. device-service also exposes lookup by
externalId (§6).

---

## 1. TECH STACK (pin versions)

- Java 21 (LTS; min 17, compatible up to Java 26), Spring Boot 4.1.x, Spring Framework 7.0.x, Spring Cloud 2025.x

> VERSION NOTE (verified current as of Oct 2026): Spring Boot 4.1.x is current
> (requires Spring Framework 7.0+, Java 17 min / 21 LTS recommended). Spring Boot 3.x
> is EOL for OSS support. These versions move fast — re-check latest stable on
> start.spring.io or the Spring release notes before you scaffold, and let
> start.spring.io pick matching dependency versions rather than pinning by hand.
- Spring Web, Spring Data JPA, Spring Security (OAuth2 Resource Server), Spring Kafka,
  Spring WebSocket, Spring Cloud Gateway, Spring AOP / AspectJ
  > Do NOT hand-type starter artifact names — Boot 4 renamed several (e.g. the AOP
  > starter is `spring-boot-starter-aspectj`) and Spring Kafka 4 uses Jackson 3
  > serializers. Generate the POM from start.spring.io.
- MySQL 8, Redis 7, Apache Kafka 4.x KRaft (or Redpanda), Keycloak 26.x
- Flyway for schema migrations (`ddl-auto=validate`, never `update`)
- Eclipse Paho MQTT client (for MQTT) OR AWS IoT Device SDK
- AWS SDK v2 (IoT, S3, DynamoDB) — use LocalStack for local dev
- Testing: JUnit 5, Mockito, Testcontainers (MySQL, Kafka, Redis), Spring Boot Test
- Build: Maven (multi-module) · Frontend: React 18 + TypeScript + Vite + Leaflet
- Containerization: Docker, docker-compose; K8s manifests (bonus)

---

## 2. MAVEN MULTI-MODULE STRUCTURE

```
fleet-tracking/
├── pom.xml                      (parent, <packaging>pom</packaging>)
├── common/                      (shared DTOs, events, constants)
│   └── src/main/java/com/fleet/common/
│       ├── event/   (TelemetryEvent, AlertEvent, DeviceStatusEvent)
│       ├── dto/
│       └── security/ (JWT role constants)
├── device-service/
├── ingestion-service/
├── telemetry-service/
├── alert-service/
├── ws-gateway/
├── api-gateway/
├── docker-compose.yml
├── keycloak/realm-export.json
└── k8s/                         (manifests, bonus)
```

Each service module: `src/main/java/com/fleet/<svc>/` with packages:
`controller/ · service/ · repository/ · entity/ · dto/ · config/ · aspect/ · exception/`
and `src/test/java/...` mirroring.

---

## 3. DATA MODELS (JPA entities)

### device-service — Device (MySQL table `device`)
```
Device {
  UUID id (PK)
  String externalId (unique, the device hardware id)
  String name
  DeviceType type        // PERSONAL_TRACKER, ASSET_TAG, VEHICLE
  DeviceStatus status    // ACTIVE, INACTIVE, LOST  (lifecycle — set by admins)
  ConnectivityStatus connectivity // ONLINE, OFFLINE (set from telemetry / LWT)
  boolean deleted        // soft-delete flag (separate from status)
  String ownerMemberId
  Instant lastSeenAt     // updated from telemetry
  Double lastLat, lastLng // denormalized last position (also in Redis)
  Integer lastBatteryPct
  Long version           // @Version optimistic locking
  Instant createdAt, updatedAt
}
```
Indexes: externalId (unique), ownerMemberId, status. Queries filter `deleted=false`.
Updates from telemetry ignore events whose `recordedAt` <= `lastSeenAt` (out-of-order).

### telemetry-service — PositionHistory (MySQL table `position_history`)
```
PositionHistory {
  Long id (PK, auto)
  String messageId       // UNIQUE — makes the consumer idempotent under redelivery
  String deviceId        // = Device.externalId
  Double lat, lng
  Double speedKmh, headingDeg
  Integer batteryPct
  Boolean sos
  Instant recordedAt     // device timestamp
  Instant ingestedAt
}
```
Indexes: (deviceId, recordedAt). Partition/retention note: in production you'd TTL
old rows or move to DynamoDB/time-series — mention this.

### alert-service — Alert (MySQL table `alert`) + Geofence
```
Alert {
  UUID id (PK)
  String deviceId
  AlertType type         // GEOFENCE_BREACH, OFFLINE, SOS, LOW_BATTERY
  AlertSeverity severity // INFO, WARNING, CRITICAL
  String message
  Double lat, lng
  UUID geofenceId        // nullable; set for GEOFENCE_BREACH
  AlertStatus status     // OPEN, ACKNOWLEDGED, RESOLVED
  String acknowledgedBy, resolvedBy
  Instant raisedAt, acknowledgedAt, resolvedAt
  Long version           // @Version
}
Geofence {
  UUID id (PK)
  String name
  GeofenceType type      // DANGER_ZONE, SAFE_ZONE
  String polygonGeoJson  // GeoJSON Polygon, coordinates are [lng, lat] (GeoJSON order!)
  Boolean active
}
DeviceAlertState {       // per-device rule state for de-duplication (Redis hash or table)
  String deviceId
  Set<UUID> insideGeofenceIds   // geofences the device is currently inside
  boolean lowBattery, sosActive, offline
}
```
**Alert de-duplication rule:** at most ONE non-RESOLVED alert per (deviceId, type,
geofenceId). Rules fire on *state transitions* (outside→inside, battery crossing below
15, sos false→true, online→offline), not on every message. Enforce in DB with a unique
partial key, e.g. column `open_key = deviceId|type|geofenceId` set to NULL on resolve.

---

## 4. EVENT SCHEMAS (Kafka topics — in `common`)

Topics: `telemetry.raw`, `telemetry.processed`, `alerts`, `device.status`

```
TelemetryEvent {
  String deviceId; double lat; double lng; double speedKmh; double headingDeg;
  int batteryPct; boolean sos; Instant recordedAt; String messageId; // for idempotency
}
AlertEvent {
  String alertId; String deviceId; String type; String severity; String message;
  double lat; double lng; Instant raisedAt;
}
DeviceStatusEvent { String deviceId; String status /* ONLINE|OFFLINE */; String source /* LWT|TIMEOUT|TELEMETRY */; Instant at; }
```
Serialization: JSON (Spring Kafka Jackson JSON serializer; type info disabled, explicit
target type per consumer). Key = deviceId (ordering per device).

**Topic ownership:**
| Topic | Producer | Consumers |
|---|---|---|
| `telemetry.raw` | ingestion-service | telemetry-service |
| `telemetry.processed` | telemetry-service | device-service, alert-service, ws-gateway |
| `device.status` | ingestion-service (LWT/online), alert-service (timeout) | device-service, ws-gateway |
| `alerts` | alert-service | ws-gateway |

**Delivery semantics:** at-least-once everywhere. Every consumer MUST be idempotent
(messageId unique key, or state-transition checks). Failed messages after 3 retries go
to `<topic>.DLT` via `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`.
ws-gateway uses a **unique consumer group per instance** (broadcast), all other
services share one group per service (work-sharing).

---

## 5. MQTT / AWS IoT INTEGRATION (the gap-closing core)

### Topic design
- `devices/{externalId}/telemetry` — periodic position
- `devices/{externalId}/sos` — SOS button
- `devices/{externalId}/status` — connect/disconnect (LWT)

Payloads:
- telemetry & sos: TelemetryEvent JSON (sos topic sets `sos=true`; SOS is QoS 1 and
  bypasses no validation except it is accepted even with stale `recordedAt`).
- status: `{"status":"online"|"offline","at":"<ISO>"}`; device publishes `online`
  (retained) on connect, LWT publishes `offline` (retained).

### Device Simulator (separate small module `device-simulator`, Python or Java)
- Publishes TelemetryEvent JSON to `devices/{id}/telemetry` every 5s for N simulated
  devices, moving along random/scripted routes.
- Sets an MQTT **Last Will & Testament** on `devices/{id}/status` = "offline" so broker
  publishes it if the device drops — demonstrates the LWT concept.
- QoS 1 for telemetry.

### Two broker options (support both; document the choice)
1. **Local dev: Mosquitto** (docker) — ingestion-service subscribes via Paho client.
2. **AWS: AWS IoT Core** — devices connect with X.509 certs; an **IoT Rule**
   per topic family — `SELECT *, topic(2) AS deviceId FROM 'devices/+/telemetry'`,
   same for `devices/+/sos`, and lifecycle events `$aws/events/presence/disconnected/+`
   (IoT Core's equivalent of LWT) — routes to either (a) a Lambda that forwards
   to the ingestion-service HTTP endpoint, or (b) directly to Kinesis/SQS.
   Also define a **Device Shadow** per device holding {status, lastPosition, battery}.
   The HTTP callback endpoint `POST /internal/iot/telemetry` is authenticated
   (shared HMAC secret or IAM-signed request from the Lambda), never public.

### ingestion-service responsibilities
- Subscribe to `devices/+/telemetry`, `devices/+/sos`, `devices/+/status` via Paho
  using **MQTT shared subscriptions** (`$share/ingestion/devices/+/telemetry`) so
  multiple instances split load; OR receive IoT-rule HTTP callbacks.
- Trust the `deviceId` from the **topic**, not the payload (reject if they differ).
- Validate payload (schema, ranges: lat[-90,90], lng[-180,180], battery[0,100],
  speed >= 0, heading [0,360), `recordedAt` not > 1 min in future and not > 24 h old;
  messageId required). Invalid → log + counter metric + drop (do not crash consumer).
- Reject unknown devices (cache of registered externalIds from device-service, Redis).
- Idempotency: dedupe by messageId — **mark-after-publish**:
  1. `EXISTS telemetry:dedupe:{messageId}` → if present, drop.
  2. Publish to Kafka and wait for ack (`acks=all`, idempotent producer).
  3. `SET telemetry:dedupe:{messageId} 1 NX EX 300`.
  A crash between 2 and 3 yields a duplicate, which downstream consumers absorb
  (PositionHistory.messageId unique). Never mark before publish — a failed publish
  would then silently drop the retry.
- Publish validated TelemetryEvent to Kafka `telemetry.raw`.
- Status messages → `device.status` (source=LWT or TELEMETRY).

---

## 6. SERVICE RESPONSIBILITIES & REST APIs

### api-gateway (Spring Cloud Gateway, 8080)
- Routes: `/api/devices/**`→device, `/api/telemetry/**`→telemetry, `/api/alerts/**`→alert, `/ws/**`→ws-gateway
- **Auth model (decided): gateway is a pass-through Resource Server, NOT a token-relay
  OAuth client.** The dashboard (public PKCE client) holds the access token and sends
  `Authorization: Bearer`; the gateway validates the JWT, forwards the header
  unchanged, and each downstream service validates it again (defense in depth).
  No server-side session, no `TokenRelay` filter.
- CORS for `http://localhost:3000` only; WebSocket upgrade passthrough for `/ws/**`.
- Errors use RFC 9457 `application/problem+json` across all services (model in `common`).

### Role matrix (all services; `ROLE_` prefix added by the JWT converter)
`operator+` = `hasAnyRole('operator','admin')`; `viewer+` = `hasAnyRole('viewer','operator','admin')`.
Declare a `RoleHierarchy` bean `admin > operator > viewer` in `common` and use
`hasRole('viewer')` style checks, OR use explicit `hasAnyRole` lists — pick the
hierarchy bean and use it consistently.

### device-service (8081)
```
POST   /api/devices                     (admin)      create device
GET    /api/devices                     (viewer+)    list (paged, filter by status/type)
GET    /api/devices/{id}                (viewer+)    get one (UUID)
GET    /api/devices/by-external/{extId} (viewer+)    get by hardware id
PUT    /api/devices/{id}                (admin)      update
DELETE /api/devices/{id}                (admin)      soft-delete (deleted=true, 204)
GET    /api/devices/{id}/status         (viewer+)    live status (Redis latest + connectivity)
```
Consumes `telemetry.processed` to update lastSeenAt/lastLat/lastLng/battery (skip if
older than lastSeenAt) and `device.status` to update connectivity.
Publishes registered/deregistered externalIds to Redis set `devices:registered`
(used by ingestion validation).

### telemetry-service (8083)
```
GET /api/telemetry/{deviceId}/latest            (viewer+) latest (Redis, fallback MySQL)
GET /api/telemetry/{deviceId}/history?from=&to= (viewer+) history (paged, max range 7 d)
```
Consumes `telemetry.raw` → inserts PositionHistory (duplicate messageId → skip, do
not republish), updates Redis latest **only if `recordedAt` is newer than the cached
value** (Lua script / WATCH for atomic compare-and-set), then publishes
`telemetry.processed`.

### alert-service (8084)
```
GET    /api/alerts?status=&deviceId=&type=  (viewer+)   list alerts (paged)
POST   /api/alerts/{id}/acknowledge         (operator+) OPEN → ACKNOWLEDGED
POST   /api/alerts/{id}/resolve             (operator+) OPEN|ACKNOWLEDGED → RESOLVED
GET    /api/geofences                       (viewer+)
POST|PUT|DELETE /api/geofences[/{id}]       (admin)     validate GeoJSON polygon (closed ring, ≥4 points)
```
Illegal alert status transitions → 409. Consumes `telemetry.processed` and
`device.status`; evaluates rules against `DeviceAlertState` (fire on transition only):
- **Geofence breach**: point-in-polygon (ray casting on [lng,lat]) against active
  DANGER_ZONE geofences; fires on outside→inside. Points exactly on the boundary
  count as **inside** (safety-biased). Active geofences cached in memory, refreshed on
  CRUD. Planar math is acceptable for small polygons; note antimeridian/large-area
  limitations in the README (production: JTS / PostGIS / MySQL spatial).
- **SOS**: sos false→true → CRITICAL. Cleared only by operator resolve.
- **Low battery**: crosses below 15 → WARNING; re-arms when battery ≥ 20 (hysteresis).
- **Offline**: two sources, both dedupe via state:
  1. `device.status` OFFLINE from LWT → immediate OFFLINE alert.
  2. Scheduled job every 60 s (ShedLock so only one instance runs it): scan Redis
     `device:lastseen` sorted set (score = lastSeen epoch, written by telemetry-service)
     for scores older than `fleet.offline-threshold` (default 5 min) → OFFLINE alert +
     publish `device.status` (source=TIMEOUT). Don't rely on Redis keyspace-expiry
     notifications (fire-and-forget, lost on restart).
  Next telemetry from an offline device auto-resolves its OFFLINE alert.
Publishes AlertEvent to `alerts` **after commit** (`@TransactionalEventListener(phase=AFTER_COMMIT)`;
stretch: transactional outbox table).

### ws-gateway (8085)
- WebSocket endpoint `/ws/live` (STOMP; native WebSocket, SockJS fallback optional).
- Subscribes to Kafka `telemetry.processed`, `alerts`, `device.status`; pushes to
  `/topic/positions`, `/topic/alerts`, `/topic/device-status`. Throttle positions to
  max 1 msg/device/second.
- **Auth:** browsers cannot set `Authorization` on a WebSocket handshake. The client
  sends the access token in the STOMP `CONNECT` frame header
  (`Authorization: Bearer …`); a `ChannelInterceptor` validates it with the
  `JwtDecoder` and sets the `Principal`. Reject SUBSCRIBE from unauthenticated
  sessions; only `viewer+` may subscribe. Disconnect sessions when token `exp` passes.

---

## 7. KEYCLOAK CONFIG (realm-export.json committed)

- Realm: `fleet`
- Clients:
  - `fleet-dashboard` (public, PKCE) — the React app
  - `fleet-gateway` (confidential) — gateway/back-channel
- Roles: `admin`, `operator`, `viewer`
- Users (seed): admin/admin123 (admin), op1/op123 (operator), view1/view123 (viewer)
  — **demo-only credentials**; README must say so. `fleet-gateway` secret via env var.
- Dashboard client: redirect URI `http://localhost:3000/*`, web origin
  `http://localhost:3000`, access token lifespan 5 min, refresh via keycloak-js.
- **Issuer consistency (common failure — get this right):** the browser reaches
  Keycloak at `http://localhost:8180`, containers at `http://keycloak:8080`. Tokens carry
  the issuer the *browser* used, so containers would reject them. Fix:
  - Keycloak: `KC_HOSTNAME=http://localhost:8180` (fixed frontend URL for all tokens),
    `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`.
  - Each service:
    ```yaml
    spring.security.oauth2.resourceserver.jwt:
      issuer-uri: http://localhost:8180/realms/fleet          # must equal token `iss`
      jwk-set-uri: http://keycloak:8080/realms/fleet/protocol/openid-connect/certs
    ```
    With `jwk-set-uri` set, Boot fetches keys from the internal URL and still validates
    `iss` (configure `JwtDecoder` with `JwtValidators.createDefaultWithIssuer(...)`).
  - Also validate `aud` contains `fleet-api` (add an audience mapper to the dashboard client).
- Method security: `@EnableMethodSecurity`, `@PreAuthorize("hasRole('admin')")` etc. Map
  realm roles → Spring authorities via a `JwtAuthenticationConverter` that reads
  `realm_access.roles` and prefixes `ROLE_`. Shared in `common/security`.
- Service-to-service / machine calls (Lambda → ingestion, scheduled jobs) use no user
  token; they run as principal `system`.

---

## 8. SPRING AOP (audit + performance — the gap-closing aspect)

In `common`, package `com.fleet.common.aspect` (auto-configured into each service):
```java
@Retention(RUNTIME) @Target(METHOD)
public @interface Audited { String action(); }

@Aspect @Component @Slf4j
@Order(Ordered.LOWEST_PRECEDENCE - 1)   // runs OUTSIDE @Transactional (see note)
public class AuditAspect {
  @Value("${spring.application.name}") private String serviceName;

  // lowercase `audited` binds the annotation to the parameter.
  // `@annotation(Audited)` (type name) + a bound param fails with "formal unbound".
  @Around("execution(* com.fleet..service..*(..)) && @annotation(audited)")
  public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String actor = (auth != null && auth.isAuthenticated()) ? auth.getName() : "system"; // Kafka/scheduler threads have no auth
    long t0 = System.nanoTime();
    try {
      Object result = pjp.proceed();
      log.info("AUDIT service={} actor={} action={} method={} outcome=OK durationMs={}",
               serviceName, actor, audited.action(), pjp.getSignature().toShortString(),
               (System.nanoTime() - t0) / 1_000_000);
      return result;
    } catch (Throwable ex) {
      log.warn("AUDIT service={} actor={} action={} method={} outcome=FAILED error={}",
               serviceName, actor, audited.action(), pjp.getSignature().toShortString(),
               ex.getClass().getSimpleName());
      throw ex;
    }
  }
}
```
Rules:
- **Do not log raw args** (`pjp.getArgs()` prints array refs and can leak member
  locations/PII). Log the action + entity id only.
- Use structured JSON logs (logstash-logback-encoder) so `AUDIT` lines are queryable.
- **Self-invocation caveat:** Spring AOP proxies only intercept calls coming from
  *outside* the bean. Put `@Audited` on public service methods called by controllers /
  listeners, never on private helpers or methods called via `this.`.
- Order: with the aspect outside the transaction, `outcome=OK` is logged only after
  commit succeeded.

Talking point: audit & timing are cross-cutting concerns → AOP keeps them out of
business logic. Annotate mutating service methods with `@Audited(action="DEVICE_CREATED")` etc.

---

## 9. CACHING (Redis)
- `device:latest:{deviceId}` → latest position JSON incl. `recordedAt` (TTL 24 h;
  compare-and-set on recordedAt). Not used for offline detection.
- `device:lastseen` → sorted set, member=deviceId, score=epoch millis (offline scan).
- `telemetry:dedupe:{messageId}` → `SET NX EX 300` after Kafka ack (idempotency).
- `devices:registered` → set of valid externalIds (ingestion validation).
- `alert:state:{deviceId}` → hash for DeviceAlertState.
- `@Cacheable` on device lookups with explicit `@CacheEvict` on update/delete.
- Redis down → ingestion dedupe **fails open** (publish anyway; downstream unique
  keys absorb duplicates) and reports a degraded health indicator.

---

## 10. AWS PIECES (document even if run on LocalStack)
- **S3**: export trip reports / device snapshots (`fleet-reports` bucket).
- **DynamoDB** (optional): raw high-volume telemetry archive (PK=deviceId, SK=recordedAt).
- **IoT Core**: broker + rule + device shadow (section 5).
- **Lambda**: IoT-rule target forwarding to ingestion, OR scheduled SLA/offline check.
- **CloudFormation**: template provisioning IoT rule, S3 bucket, DynamoDB table, (ECS).

---

## 11. TEST SPECS (TDD — write these first)

### Unit (JUnit 5 + Mockito)
- `GeofenceEvaluatorTest` — inside/outside; point exactly on edge → inside; vertex →
  inside; concave polygon; GeoJSON [lng,lat] order respected (swapped coords → outside).
- `TelemetryValidatorTest` — rejects out-of-range lat/lng/battery/heading, future and
  >24 h old `recordedAt`, missing messageId, topic/payload deviceId mismatch; accepts valid.
- `AlertRuleTest` — fires on transition only: 10 consecutive in-zone messages → 1
  alert; leave + re-enter → 2nd alert; battery 14→13→12 → 1 alert, 14→25→14 → 2;
  SOS false→true fires, true→true does not.
- `IdempotencyServiceTest` — duplicate messageId dropped; Kafka publish failure does
  NOT mark the id as seen (retry succeeds).
- `LatestPositionCacheTest` — older recordedAt does not overwrite newer.
- `AuditAspectTest` — logs actor from SecurityContext; `system` when no auth; rethrows
  and logs FAILED on exception.

### Integration (Testcontainers)
- `DeviceControllerIT` — CRUD against real MySQL container; auth enforced (401/403).
- `IngestionFlowIT` — Mosquitto (HiveMQ/Mosquitto container) → ingestion → Kafka
  `telemetry.raw`; same messageId published twice → one Kafka record.
- `TelemetryFlowIT` — publish TelemetryEvent to Kafka → telemetry-service stores + caches → latest endpoint returns it; redelivered event → still one row.
- `AlertFlowIT` — processed telemetry breaching a geofence → exactly one AlertEvent on `alerts`.
- `OfflineDetectionIT` — device silent > threshold → OFFLINE alert; next telemetry auto-resolves.
- Use Awaitility for async assertions (no `Thread.sleep`).

### Security
- Endpoints reject missing/invalid JWT (401), wrong role (403), accept correct role (200).
- Token with wrong `iss` or `aud` → 401. Use `spring-security-test` `jwt()` post-processor
  for slice tests; one IT against a real Keycloak container (dasniko testcontainers-keycloak).
- WebSocket CONNECT without token → rejected; viewer can subscribe.

Target: meaningful coverage on rule logic + flows (not 100% vanity coverage).

---

## 12. BUILD SEQUENCE (hand to Claude Code in this order)

1. Parent POM + `common` (events, DTOs, Audited annotation).
2. `device-service` — entity, repo, service (TDD), controller, security config. Tests green.
3. `docker-compose` with MySQL + Kafka + Redis + Keycloak; `keycloak/realm-export.json`.
4. Device simulator + Mosquitto; publish telemetry.
5. `ingestion-service` — MQTT subscribe, validate, dedupe, publish to Kafka.
6. `telemetry-service` — consume, store, cache, query API.
7. `alert-service` — geofence/SOS/battery/offline rules, alert API.
8. `ws-gateway` — WebSocket push.
9. `api-gateway` — routing + auth.
10. `dashboard` — React+TS: login (Keycloak), live map, device list, alert feed.
11. `AuditAspect` wired across services; `@Audited` on mutating methods.
12. AWS: swap Mosquitto→AWS IoT Core (rule + shadow); S3 report export; CloudFormation.
13. CI/CD: GitHub Actions (mvn verify + build images). K8s manifests (bonus).

**Minimum viable for interview (if short on time): steps 1-7 + 11** → covers MQTT,
AWS IoT concepts, Spring Boot microservices, JPA/Hibernate, Kafka, Redis, alerts,
Spring AOP, TDD. That alone closes the IoT gap defensibly.

**Even tighter slice (≈1–2 days):** steps 1, 3, 4, 5, 6 + geofence/SOS rules only from
step 7 + 11. Skip Keycloak wiring in services (add later) but keep the role matrix in
the README. Demo: simulator → Mosquitto → ingestion (dedupe) → Kafka → telemetry →
alert, shown via logs + `/latest` endpoint + one IT green.

### Definition of done (per step)
- `mvn verify` green, including that step's tests from §11.
- Flyway migration added; no `ddl-auto=update`.
- Actuator `/actuator/health` (readiness incl. Kafka/Redis/DB) exposed per service.
- docker-compose service has a healthcheck; dependents use `depends_on: condition: service_healthy`.

---

## 13. README / DESIGN-DOC CHECKLIST (for the repo)
- Architecture diagram (from spec).
- Design decisions: microservices boundaries, event-driven choreography, idempotency,
  offline detection strategy, why AOP for audit, MQTT QoS/LWT/device-shadow choices.
- Known limitations & production path: planar geofence math, MySQL for time-series
  (→ partitioning/TTL/DynamoDB/Timestream), log-based audit (→ App 2's immutable store),
  after-commit publish (→ transactional outbox), demo credentials.
- Observability: Micrometer metrics (ingest rate, invalid/duplicate counts, consumer
  lag), trace id propagated through Kafka headers.
- "What I personally built" paragraph.
- "Built AI-first with Claude Code/Cursor" note.
- Run instructions (docker-compose up).

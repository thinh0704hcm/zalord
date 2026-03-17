# giano — Architecture Comparison Study
## Modular Monolith vs. Microservices (School Project — Trio Team)

> A real-time chat application built twice — first as a modular monolith, then extracted into
> microservices — to produce a quantitative, reproducible comparison across four dimensions:
> developer complexity, operational complexity, performance, and resilience.
> Team of 3 · 12 weeks · AI-assisted development.

---

## Purpose & Research Questions

This project exists to answer four concrete questions with measured evidence, not opinion:

| # | Question | Measured By |
|---|----------|-------------|
| Q1 | How much more complex is the microservices codebase to build and understand? | LOC, coupling metrics, build time, `ApplicationModuleTest` violations |
| Q2 | How much harder is the microservices system to operate? | Docker Compose service count, deployment steps, config surface area, time-to-first-healthy |
| Q3 | Does the architecture change observable performance at this scale? | p50/p95/p99 latency, throughput (msg/s), WS connection time under load |
| Q4 | How do the two architectures fail differently? | Behaviour when one service/module is killed; message loss, error rates, recovery time |

Both implementations run the same functional application on the same infrastructure so that only the architecture changes between measurements. The frontend is shared and switches between backends via a single environment variable.

---

## Scope

### What is built
- A real-time group chat application (auth, rooms, messaging, presence)
- Stage 1: modular monolith with Spring Modulith
- Stage 2: four independent microservices extracted from Stage 1
- Instrumentation layer (Micrometer + Prometheus + Grafana) present in both stages
- k6 load test suite run identically against both stages
- Final comparison report with raw data, charts, and analysis

### What is explicitly not built
- Email flows (verification, password reset) — auto-verified stub only
- Social login
- Message edit / delete / pagination
- File sharing, @mentions, read receipts
- Mobile app
- Multi-region or Kubernetes deployment

### Frontend scope
The frontend is functional but minimal. It exists to drive realistic WebSocket load and to demo the comparison scenarios — not to be a polished product. Tailwind + React, no design system, no animations.

---

## Architecture Overview

### Stage 1 — Modular Monolith

```
┌─────────────────────────────────────────────────────┐
│                  giano (Spring Boot)                 │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │   auth   │  │   room   │  │     messaging     │  │
│  └────┬─────┘  └────┬─────┘  └────────┬──────────┘  │
│       │             │                 │              │
│       └─────────────┴────── events ───┘              │
│                                       │              │
│                              ┌────────┴──────────┐   │
│                              │     presence      │   │
│                              └───────────────────┘   │
└─────────────────────────────────────────────────────┘
         │                          │
    PostgreSQL                    Redis
   (single DB)               (Pub/Sub + presence)
```

All inter-module communication uses `ApplicationEventPublisher` / `@ApplicationModuleListener`. No direct `@Autowired` cross-module service injection. This is enforced by `ApplicationModuleTest` in CI from Week 1.

### Stage 2 — Microservices

```
                        ┌───────────────┐
   Browser / k6  ──────▶│  Nginx Gateway│
                        └───────┬───────┘
                                │
          ┌─────────────────────┼──────────────────────┐
          │                     │                      │
   ┌──────▼──────┐    ┌─────────▼───────┐    ┌────────▼──────┐
   │ auth-service│    │  room-service   │    │  msg-service  │
   │  :8081      │    │     :8082       │    │    :8083      │
   └──────┬──────┘    └─────────┬───────┘    └────────┬──────┘
          │                     │                     │
     auth_db               room_db                msg_db
    (Postgres)             (Postgres)            (Postgres)
                                                      │
                                          ┌───────────▼───────────┐
                                          │   presence-service    │
                                          │        :8084          │
                                          └───────────┬───────────┘
                                                      │
                                   ┌──────────────────┴──────────┐
                                   │         RabbitMQ            │
                                   │  (replaces Redis Pub/Sub    │
                                   │   for inter-service events) │
                                   └─────────────────────────────┘
                                                      │
                                                    Redis
                                               (presence only)
```

The monolith's `ApplicationEventPublisher` events map directly to RabbitMQ exchanges in Stage 2. The event payloads are identical — only the transport changes.

### Module → Service Mapping

| Module (Stage 1) | Service (Stage 2) | Own DB | Listens to (RabbitMQ) | Publishes to (RabbitMQ) |
|-----------------|-------------------|--------|----------------------|------------------------|
| `auth` | `auth-service` | `auth_db` | — | `user.registered` |
| `room` | `room-service` | `room_db` | `user.registered` | `room.joined`, `room.left` |
| `messaging` | `msg-service` | `msg_db` | `room.joined`, `room.left` | `message.sent` |
| `presence` | `presence-service` | — (Redis only) | `message.sent`, `room.joined`, `room.left` | — |

---

## Instrumentation Design

> Instrumentation is not an afterthought — it is built into the monolith in Week 5 and mirrored exactly in the microservices in Week 10. Without identical instrumentation in both stages, the comparison produces no usable data.

### Metrics collected (Micrometer → Prometheus → Grafana)

**Performance:**
```
giano_message_e2e_latency_ms        # Time from WS send to all subscribers received
giano_ws_connect_duration_ms        # WS handshake + STOMP CONNECT time
giano_http_request_duration_ms      # Per-endpoint latency histogram (p50/p95/p99)
giano_messages_per_second           # Throughput counter per room
```

**Operational:**
```
giano_db_query_duration_ms          # Per-query timing
giano_event_publish_duration_ms     # Time to publish inter-module/service event
giano_event_consume_lag_ms          # Stage 2 only: RabbitMQ consumer lag
```

**Resilience:**
```
giano_circuit_open_total            # Stage 2 only: circuit breaker open events
giano_message_loss_total            # Messages published but never confirmed received
giano_reconnect_duration_ms         # Client reconnect time after disconnect
giano_service_recovery_ms           # Stage 2 only: time from service restart to healthy
```

### Prometheus scrape config (both stages)
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'giano'
    static_configs:
      - targets:
          # Stage 1: single app
          - 'app:8080'
          # Stage 2: replace with individual services
          # - 'auth-service:8081'
          # - 'room-service:8082'
          # - 'msg-service:8083'
          # - 'presence-service:8084'
    metrics_path: '/actuator/prometheus'
```

### k6 Load Test Scenarios (identical on both stages)

Three scenarios run in sequence. Results are saved as JSON and committed to the repo.

**Scenario A — Baseline (10 concurrent users, 5 min):**
```javascript
// k6/scenarios/baseline.js
export const options = {
  scenarios: {
    chat_load: {
      executor: 'constant-vus',
      vus: 10,
      duration: '5m',
    }
  },
  thresholds: {
    'giano_message_e2e_latency_ms{quantile:"0.95"}': ['value<200'],
    'http_req_failed': ['rate<0.01'],
  }
};
```

**Scenario B — Ramp (1 → 50 users over 10 min):**
```javascript
export const options = {
  stages: [
    { duration: '2m', target: 10 },
    { duration: '4m', target: 30 },
    { duration: '2m', target: 50 },
    { duration: '2m', target: 0 },
  ]
};
```

**Scenario C — Resilience (kill one service/module mid-run):**
```bash
# Stage 1: kill messaging module handler via feature flag
# Stage 2: docker compose stop msg-service
# Record: message loss count, error rate spike, recovery time
```

---

## Stage 1 — Modular Monolith (Weeks 1–6)

### Tech Stack

| Layer | Choice |
|-------|--------|
| Backend | Spring Boot 3.x + Spring Modulith + Java 21 |
| Real-time | WebSocket + STOMP + Redis Pub/Sub |
| Database | PostgreSQL 16 via PgBouncer (transaction pool) |
| Cache / Presence | Redis 7 |
| Frontend | Vite + React 18 + TypeScript + Tailwind |
| Build | Maven |
| CI | GitLab CI |

### Infrastructure (Docker Compose — Stage 1)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: giano
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./infrastructure/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5433:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giano_user -d giano"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    ports:
      - "6380:6379"

  pgbouncer:
    image: edoburu/pgbouncer:v1.25.1-p0
    restart: unless-stopped
    environment:
      DB_HOST: postgres
      DB_USER: giano_user
      DB_PASSWORD: ${DB_PASSWORD}
      DB_NAME: giano
      POOL_MODE: transaction
      MAX_CLIENT_CONN: 100
      DEFAULT_POOL_SIZE: 25
      AUTH_TYPE: scram-sha-256
      AUTH_USER: giano_user
      AUTH_QUERY: "SELECT usename, passwd FROM pg_shadow WHERE usename=$1"
    ports:
      - "6433:5432"
    depends_on:
      postgres:
        condition: service_healthy

  app:
    build: ./backend
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://pgbouncer:5432/giano
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET}
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./infrastructure/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    volumes:
      - ./infrastructure/grafana/dashboards:/etc/grafana/provisioning/dashboards
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD}

volumes:
  postgres_data:
  redis_data:
```

### Data Model

```sql
-- users
CREATE TABLE users (
  id             BIGSERIAL PRIMARY KEY,
  email          VARCHAR(255) UNIQUE NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,
  display_name   VARCHAR(100),
  avatar_url     TEXT,
  email_verified BOOLEAN DEFAULT TRUE,  -- stub: auto-verified
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at     TIMESTAMP NULL
);

-- rooms
CREATE TABLE rooms (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(255) NOT NULL,
  description TEXT,
  created_by  BIGINT NOT NULL REFERENCES users(id),
  is_public   BOOLEAN DEFAULT TRUE,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at  TIMESTAMP NULL
);

-- room_members
CREATE TABLE room_members (
  id        BIGSERIAL PRIMARY KEY,
  room_id   BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role      VARCHAR(20) NOT NULL DEFAULT 'member',
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(room_id, user_id)
);

-- messages
CREATE TABLE messages (
  id          BIGSERIAL PRIMARY KEY,
  room_id     BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text        TEXT NOT NULL,
  sequence_id BIGINT NOT NULL,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(room_id, sequence_id)
);

-- refresh_tokens
CREATE TABLE refresh_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Critical indexes
CREATE INDEX idx_messages_room_seq     ON messages(room_id, sequence_id DESC);
CREATE INDEX idx_room_members_room     ON room_members(room_id);
CREATE INDEX idx_room_members_user     ON room_members(user_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens(expires_at);
```

### Features (Minimum Viable for Comparison)

All features serve the comparison — nothing more.

**Auth:**
- Email + password signup (bcrypt, 12 rounds). Auto-verified.
- JWT login (15 min access, 7 day refresh). Stored in localStorage.
- Silent refresh on 401. Redirect to login on expired refresh.
- WebSocket upgrade validated with JWT.

**Rooms:**
- Create room, list public rooms, join, leave.
- Membership check on every message send.

**Messaging:**
- Send via STOMP `/app/room/{roomId}/send`.
- Persist to DB with per-room `sequence_id` (`UNIQUE(room_id, sequence_id)`).
- Broadcast via Redis Pub/Sub `room:{roomId}`.
- Catch-up on reconnect: `GET /api/rooms/{id}/messages?after={seq}&limit=50`.
- Client-side message queue during disconnect; flush on reconnect.

**Presence:**
- Redis Hash `room:{roomId}:members` — set on WS connect, removed on disconnect.
- Typing indicator via `/app/room/{roomId}/typing` (debounced 300ms, clear after 2s).
- System messages on join/leave.

**Reconnection:**
- Exponential backoff (1s → 2s → 4s → max 30s) with ±20% jitter.
- "Reconnecting..." banner; "Connection lost" after 10 attempts.

**Instrumentation (added in Week 5 — not optional):**
- Micrometer with Prometheus registry on all four metric groups above.
- `/actuator/prometheus` endpoint enabled.
- Grafana dashboard provisioned from JSON in repo.

---

## Stage 2 — Microservices (Weeks 7–11)

### What changes, what stays the same

| Component | Stage 1 | Stage 2 |
|-----------|---------|---------|
| Frontend | React app → `localhost:8080` | React app → `localhost:80` (Nginx gateway) |
| Auth logic | `auth` module | `auth-service` (:8081) — code copied, not rewritten |
| Room logic | `room` module | `room-service` (:8082) |
| Messaging logic | `messaging` module | `msg-service` (:8083) |
| Presence logic | `presence` module | `presence-service` (:8084) |
| Inter-module events | `ApplicationEventPublisher` | RabbitMQ exchanges (same payload DTOs) |
| Database | Single PostgreSQL | 4 independent PostgreSQL schemas |
| WS routing | Spring in-process | `msg-service` owns WS; others notified via RabbitMQ |
| Redis | Pub/Sub + presence | Presence only (`presence-service`) |
| Gateway | None | Nginx reverse proxy |
| Instrumentation | Micrometer on `app` | Micrometer on all 4 services (identical metrics) |

### Infrastructure (Docker Compose — Stage 2)

```yaml
services:
  # ── Databases (one per service) ─────────────────────────────
  auth-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: auth_db
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - auth_db_data:/var/lib/postgresql/data
      - ./infrastructure/postgres/auth-init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giano_user -d auth_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  room-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: room_db
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - room_db_data:/var/lib/postgresql/data
      - ./infrastructure/postgres/room-init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giano_user -d room_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  msg-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: msg_db
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - msg_db_data:/var/lib/postgresql/data
      - ./infrastructure/postgres/msg-init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giano_user -d msg_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Message broker ──────────────────────────────────────────
  rabbitmq:
    image: rabbitmq:3-management-alpine
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: giano
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    ports:
      - "5672:5672"
      - "15672:15672"   # management UI
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Redis (presence only) ───────────────────────────────────
  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

  # ── Services ────────────────────────────────────────────────
  auth-service:
    build: ./services/auth-service
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://auth-db:5432/auth_db
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: giano
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    depends_on:
      auth-db:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy

  room-service:
    build: ./services/room-service
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://room-db:5432/room_db
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: giano
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      AUTH_SERVICE_URL: http://auth-service:8081
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    depends_on:
      room-db:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy

  msg-service:
    build: ./services/msg-service
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://msg-db:5432/msg_db
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: giano
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      AUTH_SERVICE_URL: http://auth-service:8081
      ROOM_SERVICE_URL: http://room-service:8082
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    depends_on:
      msg-db:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy

  presence-service:
    build: ./services/presence-service
    environment:
      SPRING_DATA_REDIS_HOST: redis
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: giano
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      AUTH_SERVICE_URL: http://auth-service:8081
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    depends_on:
      redis:
        condition: service_started
      rabbitmq:
        condition: service_healthy

  # ── API Gateway ─────────────────────────────────────────────
  gateway:
    image: nginx:alpine
    volumes:
      - ./infrastructure/nginx/gateway.conf:/etc/nginx/nginx.conf:ro
    ports:
      - "80:80"
    depends_on:
      - auth-service
      - room-service
      - msg-service
      - presence-service

  # ── Observability (same as Stage 1) ────────────────────────
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./infrastructure/prometheus/prometheus-ms.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    volumes:
      - ./infrastructure/grafana/dashboards:/etc/grafana/provisioning/dashboards
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD}

volumes:
  auth_db_data:
  room_db_data:
  msg_db_data:
  redis_data:
```

### Nginx Gateway Config

```nginx
# infrastructure/nginx/gateway.conf
events { worker_connections 1024; }
http {
  upstream auth    { server auth-service:8081; }
  upstream room    { server room-service:8082; }
  upstream msg     { server msg-service:8083; }
  upstream presence { server presence-service:8084; }

  server {
    listen 80;

    location /api/auth/    { proxy_pass http://auth/; }
    location /api/rooms/   { proxy_pass http://room/; }
    location /api/messages/{ proxy_pass http://msg/; }
    location /ws/          {
      proxy_pass http://msg/;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection "upgrade";
    }
    location /api/presence/{ proxy_pass http://presence/; }
  }
}
```

### Service Boundaries — What Each Service Owns

**auth-service** owns: `users` table, `refresh_tokens` table, JWT issuance and validation.
Other services validate tokens by calling `GET /internal/auth/validate` — a lightweight in-process check, not a DB hit on every request.

**room-service** owns: `rooms` table, `room_members` table. Calls `auth-service` for token validation. Publishes `room.joined` and `room.left` to RabbitMQ when membership changes.

**msg-service** owns: `messages` table, WebSocket server, STOMP routing. Validates membership by calling `room-service`. Publishes `message.sent` to RabbitMQ after each persisted message.

**presence-service** owns: Redis Hash presence state only (no DB). Consumes `room.joined`, `room.left`, `message.sent` from RabbitMQ. Handles typing indicator subscriptions via its own WebSocket endpoint.

---

## Phase Plan (12 Weeks)

### Week 1 — Foundation

**Goal:** Both CI and local dev environment running before writing any feature code.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| Verify Docker Compose (Postgres, Redis, PgBouncer) end-to-end | DevOps | No |
| Write `init.sql` (all tables + indexes) | Backend | High |
| Spring Boot project with all dependencies declared | Backend | High |
| Add `ApplicationModuleTest` — must pass CI from this point forward | Backend | Low |
| Vite + React + Tailwind scaffold, Vite proxy to `:8080` | Frontend | High |
| GitLab CI: `backend:test`, `frontend:build` stages green | DevOps | Medium |
| Prometheus + Grafana containers in Compose; `/actuator/health` reachable | DevOps | Medium |
| Repo structure: `backend/`, `frontend/`, `infrastructure/`, `k6/`, `comparison/` | All | — |

**Done when:** `docker compose up -d` starts all services healthy; `ApplicationModuleTest` passes CI; Vite dev server proxies to Spring Boot successfully.

---

### Week 2 — Auth + Rooms (parallel, AI-heavy)

**Goal:** Authenticated users can create and join rooms. Both features are CRUD-heavy and compress well with AI assistance.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| Auth module: signup, login, refresh, logout endpoints | Backend | High |
| JWT filter, bcrypt, `UserRegisteredEvent` published | Backend | High |
| Room module: CRUD + membership endpoints, `UserJoinedRoomEvent` / `UserLeftRoomEvent` | Backend | High |
| Axios interceptor (JWT header + silent refresh on 401) | Frontend | High |
| Login/signup forms, protected route wrapper | Frontend | High |
| Room list sidebar, create-room modal, join/leave buttons | Frontend | High |
| Postman collection: auth + room endpoints | All | Medium |

**Done when:** User A signs up, creates a room; User B signs up, joins that room; both appear in the member list.

---

### Week 3 — Messaging Core

**Goal:** Real-time message delivery working end-to-end.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| Spring WebSocket + STOMP configuration | Backend | Medium |
| `@MessageMapping` handlers: `/app/room/{id}/send`, `/app/room/{id}/typing` | Backend | Medium |
| Message service: validate → persist → publish to Redis Pub/Sub → emit `MessageSentEvent` | Backend | Medium |
| `sequence_id` generation (DB sequence per room) | Backend | Medium |
| Catch-up API: `GET /api/rooms/{id}/messages?after={seq}&limit=50` | Backend | High |
| `websocketService.ts`: StompClient wrapper (connect, subscribe, send, reconnect) | Frontend | Medium |
| `ChatWindow`: message list, input field, auto-scroll | Frontend | High |
| Reconnect logic: exponential backoff + jitter, message queue, banner | Frontend | Medium |

**Done when:** Two browser tabs exchange messages in real-time; kill network on one tab; reconnect; catch-up messages appear in correct order.

---

### Week 4 — Presence, Typing, Polish

**Goal:** Presence and typing indicators complete. Frontend functionally complete for comparison purposes.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| Redis presence: `HSET`/`HDEL` on WS connect/disconnect | Backend | High |
| Typing indicator: publish/subscribe via Redis Pub/Sub | Backend | High |
| System messages (join/leave) | Backend | Medium |
| Heartbeat: server PING every 30s; stale client removal | Backend | Medium |
| `@ControllerAdvice` exception handler (consistent JSON error body) | Backend | High |
| Member list UI with online indicators | Frontend | High |
| Typing indicator UI (debounced, auto-clear) | Frontend | High |
| Error toasts: 401 → login, 429 → retry notice, 5xx → generic | Frontend | High |

**Done when:** QA script (see Testing section) passes end-to-end manually.

---

### Week 5 — Tests + Baseline Instrumentation

**Goal:** Monolith is locked. All measurement infrastructure is in place and producing real data before Stage 2 begins.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| JUnit 5: `AuthService`, `RoomService`, `MessageService` unit tests | Backend | High |
| Testcontainers integration tests (Postgres + Redis) | Backend | Medium |
| Test edge cases: duplicate sequence_id, invalid JWT, non-member send | Backend | Medium |
| `ApplicationModuleTest` still passing (regression check) | Backend | Low |
| Add Micrometer metrics: all four metric groups (performance, operational, resilience) | Backend | Medium |
| Grafana dashboard: provision from JSON, verify all panels populate | DevOps | Medium |
| k6 Scenario A (baseline, 10 VUs, 5 min) against monolith — save results to `comparison/stage1/` | All | Low |
| k6 Scenario B (ramp 1→50) against monolith — save results | All | Low |

**Done when:** All three k6 scenarios produce saved JSON results; Grafana shows all metric panels populated; JUnit suite >80% service-layer coverage.

---

### Week 6 — Resilience Test + Stage 1 Freeze

**Goal:** Run Scenario C (kill messaging handler mid-run), record results, freeze the monolith codebase. No feature changes after this week.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| k6 Scenario C: disable messaging handler via feature flag mid-run; record message loss, error rate, recovery time | All | Low |
| Document Stage 1 baseline numbers in `comparison/stage1/results.md` | All | Low |
| Record dev complexity metrics: LOC per module, build time, time-to-first-healthy | All | Low |
| Tag git commit: `v1.0-monolith` | All | — |
| Begin Stage 2 repo structure: `services/` directory, shared `common/` library for event DTOs | Backend | Medium |

**Stage 1 baseline numbers to record:**
```
- Total LOC (backend)
- LOC per module (auth / room / messaging / presence)
- Build time (./mvnw package, cold cache)
- docker compose up → all healthy: N seconds
- Service count in Compose
- Compose config lines
- k6 Scenario A: p50 / p95 / p99 latency, throughput msg/s
- k6 Scenario B: latency at 50 VUs, error rate
- k6 Scenario C: message loss count, error rate spike, recovery time (ms)
```

---

### Week 7 — Microservices Infrastructure

**Goal:** All four service skeletons running with independent DBs, RabbitMQ wired, Nginx gateway serving traffic.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| Create 4 Spring Boot projects under `services/` (copy from monolith, strip cross-module code) | Backend | High |
| Per-service `init.sql` files (split from monolith schema) | Backend | High |
| RabbitMQ container + exchange/queue declarations | DevOps | Medium |
| Stage 2 `docker-compose.yml` (full file above) | DevOps | Medium |
| Nginx gateway config (routing table above) | DevOps | Medium |
| `common/` shared library: event DTO classes (identical to monolith event payloads) | Backend | High |
| Frontend `.env` switch: `VITE_API_URL=http://localhost:80` | Frontend | — |
| Verify: `docker compose up -d` (Stage 2) — all 4 services + RabbitMQ + Nginx healthy | All | — |

**Done when:** `GET http://localhost/api/auth/health` returns 200 through Nginx; RabbitMQ management UI shows exchanges declared.

---

### Week 8 — Service Extraction: Auth + Room

**Goal:** `auth-service` and `room-service` fully functional against their own DBs.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| `auth-service`: copy auth module logic, wire to `auth_db`, publish `user.registered` to RabbitMQ | Backend | High |
| `auth-service`: expose `GET /internal/auth/validate` for inter-service JWT checks | Backend | High |
| `room-service`: copy room module logic, wire to `room_db` | Backend | High |
| `room-service`: consume `user.registered` from RabbitMQ; publish `room.joined` / `room.left` | Backend | Medium |
| `room-service`: call `auth-service /internal/auth/validate` instead of local JWT filter | Backend | Medium |
| Frontend: verify room list and create-room flow through Nginx gateway | Frontend | Low |

**Done when:** User can sign up (hits `auth-service`), create a room (hits `room-service`), and see it in the list — all through the Nginx gateway.

---

### Week 9 — Service Extraction: Messaging + Presence

**Goal:** Full message flow working through the distributed system.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| `msg-service`: copy messaging module, wire to `msg_db` | Backend | High |
| `msg-service`: validate membership via `room-service` HTTP call before persisting message | Backend | Medium |
| `msg-service`: publish `message.sent` to RabbitMQ after persist (replaces Redis Pub/Sub for events) | Backend | Medium |
| `msg-service`: keep Redis Pub/Sub for WS broadcast within the service (internal fan-out) | Backend | Medium |
| `presence-service`: consume `room.joined`, `room.left`, `message.sent` from RabbitMQ | Backend | High |
| `presence-service`: Redis presence hash (identical logic to monolith) | Backend | High |
| End-to-end test: two browser tabs through Nginx, send/receive messages, verify presence updates | All | Low |

**Done when:** Full QA script passes against Stage 2 — same user flows, different architecture underneath.

---

### Week 10 — Instrumentation + Resilience Wiring

**Goal:** All four services instrumented identically to Stage 1. Circuit breakers in place for resilience test.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| Mirror all Micrometer metrics on all 4 services (same metric names, same tags) | Backend | High |
| Add `giano_event_consume_lag_ms` and `giano_service_recovery_ms` (Stage 2-only metrics) | Backend | Medium |
| Update Prometheus scrape config to Stage 2 targets | DevOps | Low |
| Verify Grafana dashboard shows all services' panels (no empty panels) | DevOps | Low |
| Add Resilience4j circuit breaker on `msg-service → room-service` membership check | Backend | Medium |
| Add Resilience4j circuit breaker on `room-service → auth-service` token validate | Backend | Medium |
| Reconnect + catch-up verified end-to-end in Stage 2 | All | Low |

**Done when:** Grafana shows all four services' metrics; circuit breakers visible in `/actuator/health`.

---

### Week 11 — Load Tests + Resilience Tests (Stage 2)

**Goal:** Run identical k6 scenarios against Stage 2. Run Scenario C by killing `msg-service` mid-run. Record everything.

| Task | Owner | AI-accelerated? |
|------|-------|----------------|
| k6 Scenario A against Stage 2 — save results to `comparison/stage2/` | All | Low |
| k6 Scenario B against Stage 2 — save results | All | Low |
| k6 Scenario C: `docker compose stop msg-service` mid-run; record loss, spike, recovery | All | Low |
| Record Stage 2 complexity metrics: LOC per service, build times (per service + total), time-to-healthy | All | Low |
| Count Compose config lines, service count, env var surface area | All | Low |
| Document all numbers in `comparison/stage2/results.md` | All | Low |

**Stage 2 numbers to record (mirror of Stage 1 list):**
```
- Total LOC (all services combined)
- LOC per service
- Build time per service (parallel) + total wall time
- docker compose up → all healthy: N seconds
- Service count in Compose
- Compose config lines
- k6 Scenario A: p50 / p95 / p99 latency, throughput msg/s
- k6 Scenario B: latency at 50 VUs, error rate
- k6 Scenario C: message loss count, error rate spike, recovery time (ms)
- RabbitMQ consumer lag under load (p95)
- Circuit breaker open events during Scenario C
```

---

### Week 12 — Comparison Report + Demo

**Goal:** Produce the final comparison document with raw data, analysis, and honest conclusions. Record demo video.

| Task | Owner | Notes |
|------|-------|-------|
| Populate comparison tables (see Comparison Report section below) | All | Raw data only, no editorializing |
| Write analysis: what the numbers actually show for each dimension | All | Acknowledge limitations |
| Generate charts from k6 JSON output (latency histograms, throughput over time) | DevOps | k6 has built-in HTML report |
| Write "lessons learned" section — things that surprised the team | All | Honest, not promotional |
| Presentation slides (15 min: background → architecture → methodology → results → conclusions) | All | |
| Demo video: same user flow run against both stages back-to-back | All | Screen recording |
| Final README update: how to reproduce all results from scratch | All | |

---

## Comparison Report Structure

The final report lives at `comparison/REPORT.md`. Template:

```markdown
# giano Architecture Comparison Report

## Methodology
- Hardware: [specs]
- Network: localhost Docker bridge
- Load tool: k6 vX.X
- Metrics: Micrometer → Prometheus → Grafana
- Both stages run on identical Docker Compose host

## Q1 — Developer Complexity

| Metric                        | Monolith | Microservices | Delta |
|-------------------------------|----------|---------------|-------|
| Total backend LOC             |          |               |       |
| LOC: auth                     |          |               |       |
| LOC: room                     |          |               |       |
| LOC: messaging                |          |               |       |
| LOC: presence                 |          |               |       |
| Build time (cold)             |          |               |       |
| Cross-module violations (CI)  |          |               |       |
| Shared event DTO classes      |   0      |               |       |

Analysis: [what the numbers show]

## Q2 — Operational Complexity

| Metric                          | Monolith | Microservices | Delta |
|---------------------------------|----------|---------------|-------|
| Docker Compose services         |          |               |       |
| Compose config lines            |          |               |       |
| Environment variables (total)   |          |               |       |
| docker compose up → healthy (s) |          |               |       |
| Deployment steps (VPS)          |          |               |       |
| Databases                       | 1        | 4             | +3    |
| Message brokers                 | 0        | 1 (RabbitMQ)  | +1    |

Analysis: [what the numbers show]

## Q3 — Performance (10 VUs steady, Scenario A)

| Metric             | Monolith | Microservices | Delta |
|--------------------|----------|---------------|-------|
| p50 latency (ms)   |          |               |       |
| p95 latency (ms)   |          |               |       |
| p99 latency (ms)   |          |               |       |
| Throughput (msg/s) |          |               |       |
| Error rate         |          |               |       |

Performance (50 VUs ramp, Scenario B):

| Metric             | Monolith | Microservices | Delta |
|--------------------|----------|---------------|-------|
| p95 latency at peak|          |               |       |
| Error rate at peak |          |               |       |
| RabbitMQ consumer lag p95 | N/A |             |       |

Analysis: [what the numbers show]

## Q4 — Resilience (Scenario C: kill messaging component mid-run)

| Metric                     | Monolith | Microservices | Notes |
|----------------------------|----------|---------------|-------|
| Messages lost              |          |               |       |
| Error rate spike (%)       |          |               |       |
| Time to detect failure (ms)|          |               |       |
| Recovery time (ms)         |          |               |       |
| Circuit breaker events     | N/A      |               |       |
| Affected components        | All      | msg-service only |    |

Analysis: [what the numbers show]

## Conclusions

[3–5 bullet honest conclusions drawn directly from the data]

## Limitations

[What this study cannot claim — single host, small scale, simplified app, etc.]

## Lessons Learned

[Things that surprised the team during extraction]
```

---

## CI/CD Pipeline

```yaml
stages:
  - test
  - build
  - deploy

variables:
  POSTGRES_DB: giano_test
  POSTGRES_USER: test
  POSTGRES_PASSWORD: test
  POSTGRES_HOST_AUTH_METHOD: trust

backend:test:
  stage: test
  image: eclipse-temurin:21-jdk-alpine
  services:
    - postgres:16-alpine
    - redis:7-alpine
  variables:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres/giano_test
    SPRING_DATASOURCE_USERNAME: test
    SPRING_DATASOURCE_PASSWORD: test
    SPRING_DATA_REDIS_HOST: redis
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
  script:
    - cd backend
    - ./mvnw test
  artifacts:
    when: always
    reports:
      junit: backend/target/surefire-reports/TEST-*.xml
    paths:
      - backend/target/site/jacoco/
    expire_in: 1 week
  cache:
    key: backend-$CI_COMMIT_REF_SLUG
    paths:
      - backend/.m2/repository/

frontend:build:
  stage: test
  image: node:20-alpine
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
  script:
    - cd frontend
    - npm ci
    - npm run build
  cache:
    key: frontend-$CI_COMMIT_REF_SLUG
    paths:
      - frontend/node_modules/

docker:build:
  stage: build
  image: docker:24
  services:
    - docker:24-dind
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
  variables:
    IMAGE: $CI_REGISTRY_IMAGE/backend:$CI_COMMIT_SHORT_SHA
  script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker build -t $IMAGE ./backend
    - docker push $IMAGE
    - docker tag $IMAGE $CI_REGISTRY_IMAGE/backend:latest
    - docker push $CI_REGISTRY_IMAGE/backend:latest

deploy:production:
  stage: deploy
  image: alpine:latest
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
      when: manual
  before_script:
    - apk add --no-cache openssh-client
    - eval $(ssh-agent -s)
    - chmod 400 "$SSH_PRIVATE_KEY"
    - ssh-add "$SSH_PRIVATE_KEY"
    - mkdir -p ~/.ssh && chmod 700 ~/.ssh
    - cp "$SSH_KNOWN_HOSTS" ~/.ssh/known_hosts
    - chmod 644 ~/.ssh/known_hosts
  script:
    - ssh deploy@$SSH_HOST "
        docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY &&
        docker pull $CI_REGISTRY_IMAGE/backend:latest &&
        cd /opt/giano &&
        docker compose -f docker-compose.prod.yml up -d --no-deps app
      "
```

**Required CI/CD variables:**

| Variable | Type | Notes |
|----------|------|-------|
| `DB_PASSWORD` | Variable | Shared across all services |
| `JWT_SECRET` | Variable (masked) | Min 32 chars |
| `RABBITMQ_PASSWORD` | Variable (masked) | Stage 2 only |
| `GRAFANA_PASSWORD` | Variable (masked) | |
| `SSH_PRIVATE_KEY` | File | 400 permissions |
| `SSH_KNOWN_HOSTS` | File | VPS fingerprint |
| `SSH_HOST` | Variable | VPS IP or hostname |

---

## Repository Structure

```
giano/
├── backend/                        # Stage 1 — modular monolith
│   └── src/main/java/io/giano/
│       ├── auth/
│       ├── room/
│       ├── messaging/
│       ├── presence/
│       └── GianoApplication.java
├── services/                       # Stage 2 — microservices
│   ├── common/                     # Shared event DTOs
│   ├── auth-service/
│   ├── room-service/
│   ├── msg-service/
│   └── presence-service/
├── frontend/                       # Shared — switches via .env
├── infrastructure/
│   ├── postgres/
│   │   ├── init.sql                # Stage 1 schema
│   │   ├── auth-init.sql           # Stage 2 per-service schemas
│   │   ├── room-init.sql
│   │   └── msg-init.sql
│   ├── nginx/
│   │   └── gateway.conf
│   ├── prometheus/
│   │   ├── prometheus.yml          # Stage 1 scrape config
│   │   └── prometheus-ms.yml       # Stage 2 scrape config
│   └── grafana/
│       └── dashboards/
├── k6/
│   └── scenarios/
│       ├── baseline.js             # Scenario A
│       ├── ramp.js                 # Scenario B
│       └── resilience.js           # Scenario C
├── comparison/
│   ├── stage1/
│   │   └── results.md              # Raw numbers from Week 5–6
│   ├── stage2/
│   │   └── results.md              # Raw numbers from Week 11
│   └── REPORT.md                   # Final comparison document
├── docker-compose.yml              # Stage 1
├── docker-compose.ms.yml           # Stage 2
└── .env.example
```

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Service extraction reveals cross-module coupling that wasn't visible in the monolith | Medium | High | `ApplicationModuleTest` in Week 1 catches this early. Fix before Week 6 freeze. |
| RabbitMQ consumer lag introduces measurable latency in Stage 2 — legitimate finding, not a bug | High | Low | Document as expected architectural trade-off. Worth including in Q3 analysis. |
| `msg-service → room-service` HTTP call adds p99 latency outliers | Medium | Medium | Circuit breaker (Resilience4j) prevents cascade. Document in Q4 analysis. |
| Comparison numbers are influenced by Docker networking rather than architecture | Low | Medium | Both stages run on same host same bridge network. Note limitation in report methodology. |
| Week 3 (WS + Redis) slips and compresses Stage 2 time | Medium | High | Pair-program WS wiring. Working two-tab PoC must exist by end of Week 3. This is the hard deadline. |
| Stage 1 freeze violated — feature added after Week 6 tag | Low | High | `v1.0-monolith` git tag is the reference. CI blocks merges to `main` that modify `backend/` after tag. |
| k6 scenarios produce inconsistent results between runs | Medium | Medium | Run each scenario 3 times; take median. Commit all raw JSON files. |

---

## Acceptance Criteria

**Stage 1 (complete by end of Week 6):**
- ✅ Full QA script passes manually
- ✅ `ApplicationModuleTest` passing in CI continuously
- ✅ JUnit suite >80% service-layer coverage
- ✅ All three k6 scenarios complete with saved JSON output in `comparison/stage1/`
- ✅ Grafana shows all metric panels populated with real data
- ✅ Git tag `v1.0-monolith` exists

**Stage 2 (complete by end of Week 11):**
- ✅ Full QA script passes against Stage 2 (same scenarios, Nginx gateway)
- ✅ All four services healthy in Stage 2 Docker Compose
- ✅ All three k6 scenarios complete with saved JSON output in `comparison/stage2/`
- ✅ Grafana shows all four services' metrics populated
- ✅ Scenario C (resilience) recorded with and without circuit breakers

**Comparison (complete by end of Week 12):**
- ✅ `comparison/REPORT.md` populated with all table cells filled
- ✅ All four research questions (Q1–Q4) answered with data, not claims
- ✅ Limitations section written honestly
- ✅ Presentation slides cover methodology + results in ≤15 min
- ✅ Demo video shows same user flow on both architectures back-to-back

---

**Document Version:** 3.0
**Last Updated:** 2026-03-17
**Changes from v2.0:** Complete rewrite. Added Stage 2 microservices. Restructured around 4 comparison research questions. Added instrumentation design, k6 scenarios, comparison report template, per-service Docker Compose, Nginx gateway, RabbitMQ config, repository structure. Frontend scoped to functional-only. Timeline compressed to 12 weeks with AI-assisted development assumed throughout.
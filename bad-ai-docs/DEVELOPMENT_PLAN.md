# zalord — Development Plan
## Software Implementation: Modular Monolith + Microservices

> This document covers only software development — architecture, code, and unit/integration tests.
> Deployment, infrastructure, load testing, and the comparison study are in `DEPLOYMENT_TESTING_PLAN.md`.

---

## Codebase Structure

```
zalord/
├── backend/                        # Stage 1 — modular monolith
│   └── src/main/java/io/zalord/
│       ├── auth/
│       │   ├── AuthModule.java
│       │   ├── api/                # Controllers, request/response DTOs
│       │   ├── domain/             # Entities, repositories
│       │   ├── service/            # AuthService, TokenService
│       │   └── event/              # UserRegisteredEvent (published)
│       ├── room/
│       │   ├── api/
│       │   ├── domain/
│       │   ├── service/
│       │   └── event/              # UserJoinedRoomEvent, UserLeftRoomEvent
│       ├── messaging/
│       │   ├── api/                # REST + WebSocket handlers
│       │   ├── domain/
│       │   ├── service/
│       │   ├── outbox/             # OutboxEntry entity, OutboxRelay
│       │   └── event/              # MessageSentEvent
│       ├── presence/
│       │   ├── api/
│       │   └── service/            # Redis presence, typing indicator
│       ├── common/                 # Shared: JwtUtil, error DTOs, base classes
│       └── zalordApplication.java
│
├── services/                       # Stage 2 — microservices
│   ├── common/                     # Shared library: event DTOs, JwtUtil, base config
│   │   └── src/main/java/io/zalord/common/
│   │       ├── events/             # UserRegisteredEvent, MessageSentEvent, etc.
│   │       ├── security/           # JwtUtil (identical to monolith)
│   │       └── dto/                # Shared request/response DTOs
│   ├── auth-service/
│   ├── room-service/
│   ├── msg-service/
│   └── presence-service/
│
├── frontend/                       # Shared across both stages
│   └── src/
│       ├── components/
│       │   ├── Auth/               # LoginForm, SignUpForm
│       │   ├── Chat/               # ChatWindow, MessageList, MessageInput
│       │   ├── Room/               # RoomList, RoomSidebar, MemberList
│       │   └── Common/             # Toast, LoadingSpinner, ReconnectBanner
│       ├── hooks/
│       │   ├── useAuth.ts
│       │   ├── useWebSocket.ts
│       │   ├── useRoom.ts
│       │   └── useMessages.ts
│       ├── services/
│       │   ├── api.ts              # Axios instance + JWT interceptor
│       │   ├── authService.ts
│       │   ├── roomService.ts
│       │   └── websocketService.ts # StompClient wrapper
│       └── store/                  # Zustand stores
│
└── k6/                             # Load test scripts (no infra config here)
    └── scenarios/
        ├── baseline.js
        ├── ramp.js
        ├── capacity_find.js
        ├── resilience.js
        └── independent_deploy.js
```

---

## Module Boundary Rule (enforced from Day 1)

No direct `@Autowired` cross-module service injection. Ever.

```java
// ❌ Never — tight coupling that defeats the comparison
@Service class MessagingService {
    @Autowired private UserService userService; // cross-module
}

// ✅ Always — maps directly to RabbitMQ in Stage 2
@Service class MessagingService {
    @Autowired private ApplicationEventPublisher events;
    public void sendMessage(...) {
        events.publishEvent(new MessageSentEvent(roomId, userId, text));
    }
}

@ApplicationModuleListener
class PresenceEventHandler {
    void on(MessageSentEvent e) { ... }
}
```

`ApplicationModuleTest` must pass CI from the first commit:

```java
@ApplicationModuleTest
class ModuleBoundaryTest {
    @Test
    void verifyModularity() {
        ApplicationModules.of(zalordApplication.class).verify(); // fails CI on violation
    }
}
```

---

## Stage 1 — Modular Monolith

### Phase S1-1 — Project Skeleton

**Goal:** All dependencies declared, module structure created, CI green, `ApplicationModuleTest` passing.

**Tasks:**
- Initialize Spring Boot 3.x project with all dependencies:
  `spring-modulith`, `spring-boot-starter-websocket`, `spring-boot-starter-security`,
  `spring-boot-starter-data-jpa`, `spring-data-redis`, `jjwt`, `micrometer-registry-prometheus`,
  `resilience4j`, `lombok`, `mapstruct`
- Create all 4 module packages with `package-info.java` exposing public API
- Add `ApplicationModuleTest` — this test must never be allowed to fail
- Initialize Vite + React + TypeScript + Tailwind frontend
- Configure Vite proxy (`/api` → `:8080`, `/ws` → `:8080`)
- Set up GitLab CI: `backend:test` and `frontend:build` stages

**AI acceleration:** High — boilerplate, dependency config, module scaffolding.

**Done when:** `./mvnw test` passes with `ApplicationModuleTest` green. Vite dev server starts.

---

### Phase S1-2 — Data Model

**Goal:** Database schema finalized. All entities and repositories created.

**Schema:**
```sql
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

CREATE TABLE rooms (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(255) NOT NULL,
  description TEXT,
  created_by  BIGINT NOT NULL REFERENCES users(id),
  is_public   BOOLEAN DEFAULT TRUE,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at  TIMESTAMP NULL
);

CREATE TABLE room_members (
  id        BIGSERIAL PRIMARY KEY,
  room_id   BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role      VARCHAR(20) NOT NULL DEFAULT 'member',
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(room_id, user_id)
);

CREATE TABLE messages (
  id          BIGSERIAL PRIMARY KEY,
  room_id     BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text        TEXT NOT NULL,
  sequence_id BIGINT NOT NULL,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(room_id, sequence_id)
);

-- Outbox table (used by messaging module)
CREATE TABLE outbox (
  id             BIGSERIAL PRIMARY KEY,
  aggregate_type VARCHAR(100) NOT NULL,
  aggregate_id   BIGINT NOT NULL,
  event_type     VARCHAR(100) NOT NULL,
  payload        JSONB NOT NULL,
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  published      BOOLEAN DEFAULT FALSE,
  published_at   TIMESTAMP NULL
);

CREATE TABLE refresh_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_messages_room_seq     ON messages(room_id, sequence_id DESC);
CREATE INDEX idx_room_members_room     ON room_members(room_id);
CREATE INDEX idx_room_members_user     ON room_members(user_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens(expires_at);
CREATE INDEX idx_outbox_unpublished    ON outbox(published, created_at) WHERE published = FALSE;
```

**Tasks:**
- Create JPA entities for all tables
- Create Spring Data repositories
- Write `init.sql` from schema above

**AI acceleration:** High.

**Done when:** All entities compile; repositories have correct query methods.

---

### Phase S1-3 — Auth Module

**Goal:** Signup, login, refresh, logout working. JWT validated on all protected endpoints.

**API:**
```
POST /api/auth/signup          → { accessToken, refreshToken }
POST /api/auth/login           → { accessToken, refreshToken }
POST /api/auth/refresh         → { accessToken }
POST /api/auth/logout          → 200
GET  /api/users/{id}           → UserDto
PATCH /api/users/{id}          → UserDto
```

**Key implementation details:**
- BCrypt password hashing, minimum 12 rounds
- JWT: HS256, access token 15 min, refresh token 7 days
- `email_verified = true` on signup — no email flow
- `UserRegisteredEvent` published via `ApplicationEventPublisher` after commit
- JWT filter on all routes except `/api/auth/**`
- WebSocket upgrade validates JWT from query param

**Frontend (parallel):**
- `LoginForm`, `SignUpForm` components
- `useAuth` hook: store tokens in `localStorage`, auto-refresh on 401
- Axios interceptor: attach `Authorization: Bearer <token>` to every request
- Protected route wrapper

**AI acceleration:** High — CRUD, JWT boilerplate, form components.

**Done when:** Postman: `signup → login → GET /api/users/me` all return 200 with correct data.

---

### Phase S1-4 — Room Module

**Goal:** Users can create, list, join, and leave rooms.

**API:**
```
GET    /api/rooms               → List<RoomDto>
POST   /api/rooms               → RoomDto
POST   /api/rooms/{id}/join     → 200 (idempotent)
POST   /api/rooms/{id}/leave    → 200
GET    /api/rooms/{id}/members  → List<MemberDto> (with online status from Redis)
DELETE /api/rooms/{id}          → 200 (admin only, soft delete)
```

**Key implementation details:**
- `UserJoinedRoomEvent` published on join
- `UserLeftRoomEvent` published on leave
- Membership check is the responsibility of this module — other modules consume the event, do not call this module directly
- Consumes `UserRegisteredEvent` to optionally auto-join a default room

**Frontend (parallel):**
- `RoomList` sidebar (joined rooms, browse public rooms)
- `CreateRoomForm` modal
- `MemberList` panel (names, avatars, online dot)

**AI acceleration:** High.

**Done when:** User A creates room; User B joins; both see each other in member list.

---

### Phase S1-5 — Messaging Module (Core)

**Goal:** Real-time message delivery end-to-end. This is the hardest phase — do not compress it with AI alone.

**API:**
```
GET  /api/rooms/{id}/messages?after={seqId}&limit=50   → List<MessageDto>

WS subscriptions:
  SUBSCRIBE /topic/room/{roomId}           → receive messages
  SUBSCRIBE /topic/room/{roomId}/presence  → receive join/leave/typing events

WS sends:
  SEND /app/room/{roomId}/send             → { text }
  SEND /app/room/{roomId}/typing           → { typing: true/false }
```

**Key implementation details:**

Message flow:
```
Client SEND
  → validate: member? text ≤ 5000 chars? timestamp ok?
  → @Transactional:
      INSERT INTO messages (sequence_id via DB sequence per room)
      INSERT INTO outbox (event_type='MessageSentEvent', payload=...)
  → COMMIT
  → OutboxRelay (scheduled @100ms):
      SELECT unpublished FROM outbox LIMIT 100
      → publish to Redis Pub/Sub "room:{roomId}"
      → publish MessageSentEvent via ApplicationEventPublisher
      → UPDATE outbox SET published=true
  → Redis listener → broadcast to all WS clients in room
```

Outbox pattern justification:
```
Without outbox:
  INSERT messages → COMMIT → crash → MessageSentEvent never fired → presence broken

With outbox:
  INSERT messages + INSERT outbox → COMMIT → crash
  → OutboxRelay retries on restart → event fires → presence updates
  → message_loss_total metric = 0 even under crash
```

Catch-up on reconnect:
```
Client reconnects → sends lastSeqId
  → GET /api/rooms/{id}/messages?after={lastSeqId}&limit=50
  → client sorts by sequence_id before rendering
  → client updates lastSeqId
```

**Frontend (parallel):**
- `websocketService.ts`: StompClient wrapper
  - connect with JWT in header
  - subscribe to room topic
  - exponential backoff reconnect (1s → 2s → 4s → max 30s, ±20% jitter)
  - message queue during disconnect, flush on reconnect
- `ChatWindow`: message list, input, auto-scroll to bottom
- `useMessages` hook: message state, catch-up trigger on reconnect
- `ReconnectBanner`: "Reconnecting..." / "Connection lost" states

**AI acceleration:** Medium. WS wiring and outbox relay need careful implementation.

**Done when:** Two browser tabs exchange messages. Kill network on one tab. Reconnect. Catch-up messages appear in correct order. No messages lost.

---

### Phase S1-6 — Presence Module

**Goal:** Online member list and typing indicators working in real-time.

**Key implementation details:**
- Consumes `UserJoinedRoomEvent` → `HSET room:{roomId}:members {userId} {timestamp}`
- Consumes `UserLeftRoomEvent` → `HDEL room:{roomId}:members {userId}`
- WS disconnect hook → remove from Redis, publish leave event
- Server PING every 30s; remove client after 2 missed PONGs
- Typing: `/app/room/{roomId}/typing` → publish to Redis `room:{roomId}:typing`
- Same user in 2 tabs → deduplicated in Redis Hash
- System messages on join/leave (gray, smaller text in frontend)

**Frontend (parallel):**
- Online dot on each member in `MemberList`
- "User X is typing..." banner (debounced 300ms, auto-clear after 2s)

**AI acceleration:** High — Redis operations are straightforward.

**Done when:** QA script (see below) passes end-to-end.

---

### Phase S1-7 — Instrumentation

**Goal:** All metrics in place on the monolith before Stage 2 begins. This phase must not be skipped or deferred.

**Metrics to instrument (Micrometer):**
```java
// Performance
Timer.builder("zalord.message.e2e.latency")
    .tag("room", roomId).register(meterRegistry);

Timer.builder("zalord.ws.connect.duration")
    .register(meterRegistry);

// Operational
Timer.builder("zalord.event.publish.duration")
    .tag("event_type", eventType).register(meterRegistry);

Timer.builder("zalord.db.query.duration")
    .tag("query", queryName).register(meterRegistry);

// Resilience
Counter.builder("zalord.message.loss.total")
    .register(meterRegistry);

Timer.builder("zalord.reconnect.duration")
    .register(meterRegistry);

// Outbox
Gauge.builder("zalord.outbox.unpublished.count",
    outboxRepository, r -> r.countByPublishedFalse())
    .register(meterRegistry);
```

**Tasks:**
- Add all Micrometer metrics to relevant service methods
- Enable `/actuator/prometheus` endpoint
- Write Grafana dashboard JSON (provision from file — not manual setup)
- Verify all panels populate with real data during a manual test run

**AI acceleration:** Medium — metric placement requires understanding the code.

**Done when:** Every Grafana panel shows real data. `zalord.outbox.unpublished.count` visible and accurate.

---

### Phase S1-8 — Unit & Integration Tests

**Goal:** >80% service-layer coverage. All edge cases tested.

**Test categories:**

```
AuthServiceTest:
  - signup with duplicate email → 409
  - login with wrong password → 401
  - expired access token + valid refresh → new token issued
  - logout invalidates refresh token

RoomServiceTest:
  - join room twice → idempotent, no duplicate in room_members
  - non-member tries to send message → 403
  - admin removes member → UserLeftRoomEvent published

MessageServiceTest:
  - sequence_id collision → UNIQUE constraint rejects duplicate
  - message > 5000 chars → 400
  - outbox entry created in same transaction as message
  - OutboxRelay marks entries published after successful broadcast

PresenceServiceTest:
  - same user joins from 2 tabs → appears once in Redis Hash
  - user disconnect → removed from Redis, UserLeftRoomEvent published

ModuleBoundaryTest:
  - ApplicationModules.of(zalordApplication.class).verify() → no violations
```

**Testcontainers setup:**
```java
@SpringBootTest
@Testcontainers
class MessageServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
}
```

**AI acceleration:** High for boilerplate test structure; Low for edge case logic.

**Done when:** `./mvnw verify` → >80% service-layer coverage; all tests green; `ApplicationModuleTest` passing.

---

### Stage 1 Freeze

**Git tag: `v1.0-monolith`**

After this tag:
- No feature changes to `backend/`
- No schema changes
- Instrumentation additions allowed only if they do not change business logic
- Frontend changes allowed (it's shared)

Record baseline complexity metrics at freeze:
```
Total backend LOC:          ___
LOC per module:
  auth:                     ___
  room:                     ___
  messaging:                ___
  presence:                 ___
Build time (cold, mvnw package): ___
Number of Spring beans:     ___
ApplicationModuleTest:      PASS
Test coverage:              ___%
```

---

## Stage 2 — Microservices

### Phase S2-1 — Shared Library + Service Skeletons

**Goal:** `common/` library published locally. All 4 service skeletons compile.

**Tasks:**
- Create `services/common/` Maven module:
  - Copy event DTOs from monolith (identical payload classes)
  - Copy `JwtUtil` (identical)
  - Base `SecurityConfig` for inter-service JWT validation
  - Base `RabbitMQConfig` (exchange + queue declarations)
- Scaffold 4 Spring Boot projects under `services/`:
  - Each depends on `common/`
  - Each has its own `application.yml` with service-specific config
  - Remove `spring-modulith` dependency (not needed in microservices)
  - Add `spring-amqp` (RabbitMQ) to all services

**AI acceleration:** High.

**Done when:** `mvn package` succeeds for all 4 services.

---

### Phase S2-2 — Auth Service + Room Service

**Goal:** Copy and adapt auth and room logic to standalone services.

**auth-service changes from monolith:**
- Remove `ApplicationEventPublisher` → publish `user.registered` to RabbitMQ exchange
- Add `GET /internal/auth/validate` endpoint (JWT validation for other services):
  ```java
  @GetMapping("/internal/auth/validate")
  public ResponseEntity<UserPrincipalDto> validate(
      @RequestHeader("Authorization") String token) { ... }
  ```
- Wire to `auth_db` (own PostgreSQL schema)

**room-service changes from monolith:**
- Replace `ApplicationEventPublisher` → publish `room.joined`, `room.left` to RabbitMQ
- Consume `user.registered` from RabbitMQ (replaces `@ApplicationModuleListener`)
- Replace JWT filter with HTTP call to `auth-service /internal/auth/validate`
  - Add Resilience4j circuit breaker on this call
- Wire to `room_db`

**AI acceleration:** High — mostly copy + adapt.

**Done when:** Signup hits `auth-service`; create room hits `room-service`; both events visible in RabbitMQ management UI.

---

### Phase S2-3 — Messaging Service + Presence Service

**Goal:** Full message flow working through all 4 services.

**msg-service changes from monolith:**
- Replace membership check (was module-internal) → HTTP call to `room-service /internal/rooms/{id}/members/{userId}`
  - Add Resilience4j circuit breaker on this call
- Replace `ApplicationEventPublisher` → keep outbox pattern, but relay publishes to RabbitMQ instead of Redis
- Keep Redis Pub/Sub **within the service** for WebSocket fan-out to connected clients
- Wire to `msg_db`

**presence-service changes from monolith:**
- Remove all DB dependency (presence is Redis-only, as in monolith)
- Consume `room.joined`, `room.left`, `message.sent` from RabbitMQ
- Redis presence logic identical to monolith
- WS typing endpoint identical to monolith

**Inter-service call map:**
```
Client → Nginx → msg-service
  msg-service → room-service: "is this user a member?" (sync HTTP)
  msg-service → msg_db: INSERT messages + outbox (ACID)
  OutboxRelay → RabbitMQ: publish message.sent
  RabbitMQ → presence-service: update presence
  msg-service → Redis Pub/Sub: fan-out to WS clients
```

**AI acceleration:** Medium — circuit breaker wiring, RabbitMQ consumer config.

**Done when:** Full QA script passes against Stage 2 through Nginx gateway.

---

### Phase S2-4 — Mirror Instrumentation

**Goal:** Identical metric names on all 4 services. Add Stage 2-only metrics.

**Additional Stage 2 metrics:**
```java
// Consumer lag (presence-service, msg-service)
Gauge.builder("zalord.rabbitmq.consumer.lag",
    rabbitTemplate, t -> getQueueDepth(t))
    .tag("queue", queueName).register(meterRegistry);

// Circuit breaker events (msg-service, room-service)
// Resilience4j auto-registers these with Micrometer:
// resilience4j.circuitbreaker.calls{name, kind}
// resilience4j.circuitbreaker.state{name}

// Service recovery time (all services)
Timer.builder("zalord.service.recovery.duration")
    .register(meterRegistry);

// Inter-service call latency (msg-service, room-service)
Timer.builder("zalord.interservice.call.duration")
    .tag("target", "room-service").register(meterRegistry);
```

**AI acceleration:** High.

**Done when:** Grafana shows all 4 services' metrics. RabbitMQ consumer lag visible. Circuit breaker state visible.

---

### Phase S2-5 — Unit & Integration Tests (Services)

**Goal:** Each service independently testable with its own Testcontainers setup.

**Key tests to add beyond Stage 1:**
```
msg-service:
  - room-service down → circuit breaker opens → fallback response
  - OutboxRelay: RabbitMQ down → entries stay unpublished → retry on reconnect
  - message.sent published to RabbitMQ after successful persist

presence-service:
  - RabbitMQ consumer processes room.joined → Redis Hash updated
  - RabbitMQ consumer idempotent (duplicate event → no duplicate in Hash)

room-service:
  - auth-service down → circuit breaker opens → 503 returned
```

**AI acceleration:** High for structure; Medium for circuit breaker tests.

**Done when:** All service tests green. Each service can be tested in isolation with mocked HTTP dependencies.

---

### Stage 2 Freeze

**Git tag: `v2.0-microservices`**

Record complexity metrics at freeze:
```
Total LOC (all services):   ___
LOC per service:
  auth-service:              ___
  room-service:              ___
  msg-service:               ___
  presence-service:          ___
  common/:                   ___
Build time per service (parallel): ___
Build time total (wall clock): ___
Number of Spring beans (per service): ___
Test coverage (per service): ___
Shared event DTO classes:    ___
```

---

## Frontend — Shared

The frontend is built once and switches between stages via environment variable:

```bash
# Stage 1
VITE_API_BASE_URL=http://vps1-ip:8080

# Stage 2
VITE_API_BASE_URL=http://vps1-ip:80  # Nginx gateway
```

**Scope: functional, not polished.** No design system, no animations, no mobile responsiveness. It exists to drive realistic WS load and demo the comparison.

**Components needed:**
- `LoginForm`, `SignUpForm` — minimal forms, no validation UI beyond error toasts
- `RoomList` sidebar — list, join button, last message preview
- `ChatWindow` — message list, input, send button
- `MemberList` — names + online dot
- `ReconnectBanner` — "Reconnecting..." / "Connection lost. Refresh?"
- `Toast` — error/success notifications

---

## QA Script (Manual End-to-End)

Run against both stages to confirm functional equivalence:

```
1. Open two browser windows → Window A, Window B
2. Window A: signup (email-a@test.com) → login
3. Window B: signup (email-b@test.com) → login
4. Window A: create room "test-room"
5. Window B: join "test-room" → verify message history loads
6. Window A: send "hello from A" → verify Window B receives instantly
7. Window B: start typing → verify Window A shows typing indicator
8. Window B: send "hi A" → verify Window A receives
9. Window A: disconnect network (DevTools → Offline)
10. Window B: send 3 messages while A is offline
11. Window A: reconnect → verify "Reconnecting..." banner → catch-up messages appear in order
12. Verify member list shows both users online
13. Window A: leave room → verify system message "User A left" in Window B
```

---

## Summary: What Belongs in This Document

| Belongs here | Does NOT belong here |
|-------------|---------------------|
| Module/service code | Docker Compose files |
| Entity definitions | VPS setup |
| Business logic | WireGuard config |
| Unit + integration tests | k6 scenarios |
| API contracts | Prometheus/Grafana config |
| Frontend components | Deployment scripts |
| Instrumentation (Micrometer) | Load test results |
| Git tags | Comparison report |

---

**Document Version:** 1.0
**Companion document:** `DEPLOYMENT_TESTING_PLAN.md`

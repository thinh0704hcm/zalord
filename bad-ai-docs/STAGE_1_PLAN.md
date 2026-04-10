# Zalord — Learning-and-Comparison Plan

## Comparison Question

> What does a small real-time messaging app actually gain or lose when its logic is split from a modular monolith into separate services — in terms of transactional consistency, operational complexity, and cross-module coupling?

---

## Fixed Product Scope (same across both stages)

- Phone-number registration and login (JWT)
- Group and direct chats
- Text message sending and history
- Real-time delivery over WebSocket/STOMP
- Member roles (OWNER / ADMIN / MEMBER)

This scope does not change between Stage 1 and Stage 2. No new features after Stage 1 is frozen.

---

## Stage 1 — Monolith Baseline

**Goal:** A correct, testable, measurable monolith.
**Exit condition:** All eight gates at the bottom of this section must pass before any Stage 2 work begins.

---

### M1 — Schema as Migrations

**Goal:** Replace `init.sql` with Flyway migrations.

**Why this matters for the comparison:** In Stage 2, each service owns its own schema. Flyway lets you document and replay schema evolution — without it you cannot make a fair comparison of schema management complexity.

**Learning objective:** Schema ownership and migration discipline as a foundation for later service extraction.

**Scope included:** Add Flyway dependency; convert `init.sql` into `V1__init.sql`. Add `spring.flyway.schemas` property.
**Scope excluded:** Schema changes, new tables, data seeding.

**Acceptance criteria:**
- `./mvnw test` passes with Flyway applied to a fresh DB
- `flyway:info` shows one applied migration at V1

**Evidence:** Screenshot of `flyway:info` output.

**Main risks:** `ddl-auto: validate` may conflict — verify Flyway runs before Hibernate validation.

**Estimated size:** Half a day (6h). AI accelerates the migration file and config well.

---

### M2 — Messaging Tests (Service Layer)

**Goal:** Unit tests for `ChatService` and `MessageService`.

**Why this matters for the comparison:** Tests define what the module contract is. You cannot extract a service safely without knowing exactly what behavior is guaranteed. Without this, Stage 2 is guesswork.

**Learning objective:** Where transactional consistency currently lives (single `@Transactional` spans chat + member in one DB call — this is the key fact the microservice stage must replicate or sacrifice).

**Scope included:** Unit tests for: create chat, send message, leave chat (owner succession path), remove member, role enforcement.
**Scope excluded:** Integration tests, WebSocket, controller layer.

**Acceptance criteria:**
- 10+ test cases covering happy paths and at least 5 error paths
- All tests tagged so they can run without Docker (`./mvnw test -Dgroups=unit-messaging`)

**Evidence:** Test output showing pass count and coverage of `ChatService` and `MessageService`.

**Main risks:** `ChatService.leaveChat` has a subtle owner-succession branch — easy to miss.

**Estimated size:** 2–3 days (20h). AI scaffolds test structure fast; behavior analysis and debugging test failures resist acceleration.

---

### M3 — Message History Endpoint

**Goal:** `GET /chats/{chatId}/messages?cursor=&limit=` returns paginated message history.

**Why this matters for the comparison:** This is the read path. In microservices, the messaging service must serve this alone without joining to other schemas. You need to know what data it requires while still in the monolith.

**Learning objective:** What data does a message response need from outside the `messaging` schema? (Sender display name? Auth info?) — this reveals a real dependency before extraction.

**Scope included:** Cursor-based pagination on `messages.created_at DESC`. Auth guard (must be chat member). Response includes `senderId`, `contentType`, `payload`, `createdAt`.
**Scope excluded:** Sender profile enrichment (name/avatar) — deliberately deferred to reveal the dependency gap.

**Acceptance criteria:**
- `curl` with valid JWT returns first page of messages
- Non-member returns 403
- Empty chat returns empty list with no error

**Evidence:** `curl` transcript saved as `docs/evidence/M3-message-history.sh`.

**Main risks:** `messages_chat_id_idx` is already on `(chat_id, created_at desc)` — verify the query uses it (`EXPLAIN ANALYZE`).

**Estimated size:** 1–2 days (12h). Cursor correctness and the auth/membership guard take longer than the initial implementation.

---

### M4 — WebSocket Send and Delivery

**Goal:** A connected client receives a message in real time via STOMP over WebSocket.

**Why this matters for the comparison:** This is the hardest module boundary to extract. In a monolith, STOMP broker, auth, and messaging share one JVM. In microservices, delivery requires a message broker and cross-service subscription. You must understand the monolith shape before splitting it.

**Learning objective:** Where does real-time delivery couple to auth (JWT over WS handshake) and to messaging (persistence before broadcast)?

**Scope included:** Spring WebSocket + STOMP; JWT authentication at handshake; publish to `/topic/chats/{chatId}` on `sendMessage`; basic frontend or `wscat` verification.
**Scope excluded:** Presence/online indicators, read receipts, Redis pub/sub.

**Acceptance criteria:**
- Two clients in the same chat both receive a message sent by one of them
- Unauthenticated WS connection is rejected
- Message is persisted to DB before broadcast (verify with DB query after disconnect)

**Evidence:** Screen recording or `wscat` terminal transcript; DB row count confirming persistence.

**Main risks:** JWT over WS handshake requires passing the token in the `Authorization` header or query param — test this explicitly.

**Estimated size:** 4–5 days (36h). 🔴 HIGH RISK. WS auth, persist-before-broadcast ordering, and multi-client debugging all resist AI acceleration. Do not compress this milestone.

---

### M5 — Module Boundary Audit

**Goal:** Document where each module's boundary currently is and where it leaks.

**Why this matters for the comparison:** This is the primary input to Stage 2. You cannot plan extraction without knowing which dependencies are clean and which are accidental.

**Learning objective:** Understand which cross-module dependencies are intentional design vs. shortcuts that will cause pain during extraction.

**Scope included:** For each module (auth, user, messaging, presence, common), write a one-page ADR that answers: what does this module own, what does it consume from other modules, and how (event, direct call, shared table)?
**Scope excluded:** Refactoring the boundaries. Audit only.

**Acceptance criteria:**
- One ADR per module in `docs/adr/` — five files total: auth, user, messaging, presence, common
- Each ADR explicitly lists cross-module dependencies
- `common.security` coupling (auth reads `CustomUserDetailsService` which reads `CredentialRepository`) is documented as a known coupling point

**Evidence:** Five ADR files committed.

**Main risks:** You may discover that `messaging` directly reaches into `user` or `auth` tables — document this even if you cannot fix it yet. ADR writing is reasoning-heavy and resists AI acceleration — budget the full time.

**Estimated size:** 2 days (14h). AI can draft structure; humans must own every dependency call-out.

---

### M7 — Presence Module (Online / Offline)

**Goal:** A connected client's online status is visible to others in the same chat, and goes offline when the WebSocket disconnects.

**Why this matters for the comparison:** Presence is the only module that is stateful by nature. In the monolith, online status is trivially derived from in-memory WebSocket sessions — no external state needed. In Stage 2, it becomes a dedicated service that must subscribe to connection events from the messaging service via a broker, and store state in Redis. This is the sharpest architectural contrast the comparison can produce. Without it, Stage 2 consists only of stateless REST services and misses the most interesting case.

**Learning objective:** Understand where session state lives in the monolith (JVM memory + Redis) and why that boundary is the hardest one to move in Stage 2.

**Scope included:** Track connected user IDs per chat in Redis (`SADD`/`SREM`); publish online/offline state changes via `ApplicationEventPublisher` from the WebSocket connect/disconnect handlers; expose `GET /chats/{chatId}/presence` returning a list of online member IDs.
**Scope excluded:** Typing indicators, last-seen timestamps, push notifications, per-device tracking.

**Acceptance criteria:**
- User A connects → User B (already in chat) sees User A appear in `GET /presence`
- User A disconnects → User B sees User A removed
- Redis key is cleaned up on disconnect (no stale entries after 60 s)

**Evidence:** `wscat` + `curl` transcript showing connect → presence update → disconnect → presence cleared. Redis `SMEMBERS` output confirming cleanup.

**Main risks:** Spring WebSocket disconnect events fire unreliably on unclean disconnects (browser tab close) — document this as a known limitation and a comparison data point (Stage 2 presence-service has the same problem).

**Estimated size:** 3 days (24h). 🔴 HIGH RISK. Disconnect semantics, Redis TTL cleanup, and end-to-end flakiness all resist AI acceleration. Budget the full time.

---

### M6 — Baseline Metrics Capture

**Goal:** Capture startup time, request latency (p50/p99), and throughput for the core flows.

**Why this matters for the comparison:** Without this, the Stage 2 comparison has no numbers. "Microservices are slower" or "faster" is meaningless without a monolith baseline.

**Learning objective:** How to instrument a Spring Boot app minimally (Actuator + Micrometer) and read the output.

**Scope included:** Spring Boot Actuator; enable `/actuator/metrics`; manually run 100 requests of `POST /messages` and `GET /chats/{id}/messages` with a simple script and record p50/p99 from the metrics endpoint.
**Scope excluded:** Grafana, Prometheus, k6, full load testing.

**Acceptance criteria:**
- `curl /actuator/metrics/http.server.requests` returns data
- A `docs/evidence/M6-baseline-metrics.md` file records: startup time, p50/p99 for send-message and message-history, DB query count per request (from `show-sql` logs)

**Evidence:** The metrics file, with raw numbers and methodology.

**Main risks:** `show-sql: true` is already set — parse it carefully to count queries per request (N+1 issues will show here).

**Estimated size:** 1–2 days (10h). The methodology write-up and interpreting Actuator output under realistic conditions take more time than the instrumentation itself.

---

## Stage 1 Exit Gates

Do not plan Stage 2 until every item here is verifiably true:

| Gate | How to verify |
|---|---|
| End-to-end messaging slice works | `wscat` send → receive, DB confirms persistence |
| Messaging tests are green | `./mvnw test` passes with 10+ messaging cases |
| Schema is migration-based | `flyway:info` shows V1 applied on fresh DB |
| Module boundaries are documented | Five ADRs exist in `docs/adr/` (auth, user, messaging, presence, common) |
| Local startup is repeatable | `docker-compose up && ./mvnw spring-boot:run` works from scratch |
| Baseline metrics are captured | `docs/evidence/M6-baseline-metrics.md` exists with numbers |
| Presence works end-to-end | Connect/disconnect cycle updates Redis and `GET /presence` reflects it |
| Transactional consistency is explained | ADR for messaging documents which operations span multiple tables in one transaction |
| Future service boundaries are identifiable | Each ADR names likely extraction point and the main obstacle |

---

## Deliberately Out of Scope for Stage 1

- Redis pub/sub (presence uses Redis directly via `SADD`/`SREM`, not pub/sub — pub/sub is a Stage 2 concern)
- Typing indicators, last-seen timestamps
- Read receipts
- File/image message types
- Frontend beyond basic WS connectivity test
- Microservice extraction of any kind
- Kubernetes, service mesh, distributed tracing

---

## Week-by-Week Schedule

Capacity: 2 people × 3h/day × 4 days/week = **24 person-hours per week**.

| Week | Focus | Hours |
|---|---|---:|
| 1 | M1 + M2 start | 24 |
| 2 | M2 finish + M3 | 24 |
| 3 | M4 part 1 (setup, JWT handshake, first message delivery) | 24 |
| 4 | M4 part 2 (multi-client, persist-before-broadcast, debugging) | 24 |
| 5 | M7 (presence module) | 24 |
| 6 | M5 (ADRs) + M6 (metrics) + evidence packaging | 24 |
| 7 | **BUFFER** — integration bugs, repeatable startup, partner review, DESIGN.md + GUIDE.md | 24 |
| 8 | **BUFFER** — comparison report, demo prep, Stage 1 freeze sign-off | 24 |

**Total scheduled: 192h.**

If Week 7 finishes with significant slack (M4 and M7 both exited on time), spend at most 8h on an auth-service extraction spike. Do not commit to full Stage 2 implementation — it requires ~174h the plan does not have.

---

## What Stage 2 Will Ask (not planned yet)

Stage 2 will extract one service at a time and measure each extraction against the Stage 1 baseline. The likely first extraction candidate is `auth` (cleanest boundary) or `presence` (most interesting because of the state management shift from in-JVM Redis to broker-mediated event flow). That decision is made after the M5 ADRs exist and only if Stage 1 exits before Week 7.

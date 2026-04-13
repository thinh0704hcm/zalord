# Roadmap

---

## Stage 1 — Modular Monolith Milestones

Complete all milestones and pass the exit gate before starting Stage 2.

| Milestone | Description | Status | Evidence |
|---|---|---|---|
| M1 | Flyway migrations replace `init.sql` | ❌ | `docs/evidence/M1-flyway-info.txt` |
| M2 | 10+ messaging unit tests passing | ❌ | `docs/evidence/M2-test-output.txt` |
| M3 | Cursor-based message history | ✅ | `docs/evidence/M3-message-history.sh` |
| M4 | WebSocket / STOMP send & receive | ✅ | `docs/evidence/M4-wscat-transcript.txt` |
| M5 | ADR audit — all coupling violations documented | ❌ | `docs/evidence/M5-adr-summary.md` |
| M6 | Actuator metrics + baseline performance captured | ❌ | `docs/evidence/M6-baseline-metrics.md` |
| M7 | Presence module (Redis-backed) | ❌ | `docs/evidence/M7-presence-transcript.txt` |

### M5 Checklist (boundary audit)

```
- [ ] Fix: user module listener for UserRegisteredEvent (user.users is currently empty)
- [ ] Implement: ChatAccessPort in messaging + ChatAccessAdapter in chat
- [ ] Document: messaging → chat coupling decision in ADR
- [ ] Document: common → auth coupling history in ADR (now resolved — CustomUserDetailsService moved to auth)
- [ ] Document: no cross-schema FK constraints rationale in ADR
```

### Stage 1 Exit Gate

**Stage 2 does not begin until every box is checked.**

```
- [ ] docker compose down -v && docker compose up -d  — fresh DB, all services healthy
- [ ] ./mvnw spring-boot:run                          — starts without errors on first run
- [ ] curl /actuator/health                           — {"status":"UP"}
- [ ] POST /api/auth/register                         — 201 + valid JWT
- [ ] POST /api/auth/login                            — 200 + valid JWT
- [ ] flyway:info shows V1 applied                    — (M1)
- [ ] ./mvnw test — all green                         — (M2)
- [ ] docs/evidence/ has files for M1–M7              — thesis baseline captured
```

**Thesis note:** M6 baseline metrics (startup time, p50/p99 latency, DB query count per request) are the quantitative Stage 1 reference. Without them, the Stage 2 comparison has nothing to measure against.

---

## Stage 2 — 3-Month Roadmap

## Team Split

| Person | Domain | Services Owned |
|---|---|---|
| **Dev 1** (Java-heavy) | Identity + Groups + Infra | Auth Service, Group Service, Docker Compose setup |
| **Dev 2** (Go-heavy) | Real-time Core | Chat Service, Push Service, Message Relay |
| **Dev 3** (Mixed) | Storage + Notifications + Media | Message Service (Java), Notification Service (Go), Media Service (Java) |

> Dev 1 also owns the initial Docker Compose + Nginx setup in Sprint 1 since it unblocks everyone else.

---

## Sprint Overview

| Sprint | Weeks | Theme |
|---|---|---|
| Sprint 1 | Week 1–3 | Foundation: infra, auth, skeleton services |
| Sprint 2 | Week 4–7 | Core chat: real-time messaging, storage, delivery |
| Sprint 3 | Week 8–10 | Full features: media, notifications, groups, search |
| Sprint 4 | Week 11–12 | Polish: testing, load testing, documentation, demo |

---

## Sprint 1 — Foundation (Week 1–3)

**Goal:** Everyone can run the full stack locally. Auth works end-to-end.

### Dev 1
- [ ] Set up `docker-compose.yml` with all infra services (Postgres, Redis, Kafka, ScyllaDB, MinIO, Nginx)
- [ ] Create shared `.env`, network, volume definitions
- [ ] Implement Auth Service: register, login, JWT issue/refresh
- [ ] Write `infra/postgres/init.sql` with all table schemas

### Dev 2
- [ ] Scaffold Chat Service (Go): HTTP server, WebSocket upgrade, JWT auth middleware
- [ ] Scaffold Push Service (Go): Kafka consumer stub, WebSocket server
- [ ] Scaffold Message Relay (Go): outbox poll loop stub

### Dev 3
- [ ] Scaffold Message Service (Java): Kafka consumer stub, ScyllaDB connection, CQL schema
- [ ] Scaffold Media Service (Java): MinIO connection, Presigned URL endpoint
- [ ] Scaffold Notification Service (Go): Kafka consumer stub, FCM client stub

### Sprint 1 Done Criteria
- `docker-compose up` starts all containers without error
- POST `/api/auth/register` and `/api/auth/login` return valid JWTs
- All service skeletons compile and start

---

## Sprint 2 — Core Chat (Week 4–7)

**Goal:** Send and receive messages end-to-end in real time. 1-on-1 chat works fully.

### Dev 1
- [ ] User Service: profile CRUD, presence (set/get via Redis)
- [ ] User Service: friend request flow (send, accept, decline)
- [ ] gRPC server on User Service: `GetUserInfo`, `GetPresence`

### Dev 2
- [ ] Chat Service: receive message via WebSocket
- [ ] Chat Service: Redis `INCR` for sequence_id
- [ ] Chat Service: atomic write to `messages` + `outbox` (Postgres transaction)
- [ ] Message Relay: poll outbox, publish to Kafka `chat.messages`, mark PROCESSED
- [ ] Push Service: consume `chat.messages` from Kafka
- [ ] Push Service: Redis Pub/Sub for cross-instance routing
- [ ] Push Service: deliver `message.new` event to recipient via WebSocket

### Dev 3
- [ ] Message Service: consume `chat.messages`, persist to ScyllaDB
- [ ] Message Service: REST API for message history (cursor-based pagination)
- [ ] Notification Service: consume `chat.messages`, check presence, send FCM

### Sprint 2 Done Criteria
- User A sends a message → User B receives it via WebSocket in real time
- Message is persisted in ScyllaDB
- Offline User B receives an FCM push notification
- Message history endpoint returns correct paginated results

---

## Sprint 3 — Full Features (Week 8–10)

**Goal:** All features from the requirements table are working.

### Dev 1
- [ ] Group Service: create group, add/remove members, role management
- [ ] Group Service: gRPC `IsMember`, `GetMembers`, `CanSend`
- [ ] Group Service: admin-only send mode, pinned messages, announcements
- [ ] Chat Service: group message fan-out support
- [ ] User Service: read receipts, typing indicators (with group < 50 gate)
- [ ] User Service: presence privacy settings (`GET/PUT /me/privacy`)

### Dev 2
- [ ] Push Service: multi-device fan-out (all active sessions per user)
- [ ] Push Service: `message.edited` and `message.deleted` events
- [ ] Push Service: typing indicator events (with group size gate)
- [ ] Push Service: read receipt events
- [ ] Push Service: presence update events (friend online/offline)

### Dev 3
- [ ] Media Service: full Presigned URL flow (upload-url → confirm → download)
- [ ] Media Service: file metadata persistence, MIME/size validation
- [ ] Notification Service: Web Push (VAPID) support alongside FCM
- [ ] Notification Service: toggleable notification content (preview vs generic)
- [ ] Message Service: edit history endpoint
- [ ] Message Service: soft delete (unsend) handling
- [ ] Message Service: per-conversation search + global search

### Sprint 3 Done Criteria
- Group chat works with roles, pinned messages, and admin-only mode
- File/image/video upload and display in chat
- Message edit and unsend work and propagate to all devices
- Typing indicators and read receipts visible in client
- Per-conversation and global message search returns results
- Push notifications arrive on both mobile (FCM) and browser (Web Push)

---

## Sprint 4 — Polish & Demo (Week 11–12)

**Goal:** Stable, documented, demonstrable system.

### All Devs
- [ ] Load test: simulate 10K concurrent WebSocket connections with `k6`
- [ ] Fix any bottlenecks found during load test
- [ ] Add `GET /health` to each service
- [ ] Write per-service README (setup, env vars, API reference)
- [ ] Resolve all `TBD` items in docs (OTP, presence privacy scope, announcements format)
- [ ] Record architecture walkthrough video or prepare demo slides
- [ ] GitLab CI/CD pipeline: build → lint → Docker push → deploy via SSH

### Optional (if time allows)
- [ ] Prometheus + Grafana metrics dashboard (~300MB RAM combined)
- [ ] K3s manifests as bonus deployment reference
- [ ] Rate limiting in Nginx per user/IP
- [ ] Simple web frontend (React) for demo

---

## Key Milestones

| Date | Milestone |
|---|---|
| End of Week 3 | Full stack running locally, auth complete |
| End of Week 7 | 1-on-1 real-time chat working end-to-end |
| End of Week 10 | All features complete |
| End of Week 12 | Demo-ready, load tested |

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ScyllaDB learning curve | Medium | Medium | Start ScyllaDB setup in Sprint 1, use simple CQL first |
| 8GB VPS runs out of RAM | Medium | High | Follow resource limits in docker-compose; swap ScyllaDB for PostgreSQL as fallback |
| Kafka Outbox pattern complexity | Low | High | Dev 2 prototypes relay in isolation before integrating with Chat Service |
| Sprint 2 underdelivery (real-time is hard) | Medium | High | Cut typing indicators and read receipts to Sprint 3 if needed |
| gRPC setup time | Low | Low | Use REST for internal calls in Sprint 1–2, migrate to gRPC in Sprint 3 if needed |

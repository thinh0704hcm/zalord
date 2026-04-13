# Architecture

---

## Stage 1 — Modular Monolith

> The current running system. All modules run in a single Spring Boot JVM.

```
┌─────────────────────────────────────────────────────┐
│                  Spring Boot JVM                    │
│                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │   auth   │  │   user   │  │      chat        │  │
│  │ register │  │ User     │  │ chats/members/   │  │
│  │ login    │  │ UserRepo │  │ roles            │  │
│  └────┬─────┘  └──────────┘  └────────┬─────────┘  │
│       │  UserRegisteredEvent           │ ChatAccessPort
│       └───────────────────────────────┘            │
│                                                     │
│  ┌──────────────────────────┐                       │
│  │        messaging         │                       │
│  │  send/delete/history     │                       │
│  └──────────────────────────┘                       │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │                  common                      │   │
│  │  security (JWT, filters, SecurityConfig)     │   │
│  │  events (UserRegisteredEvent)                │   │
│  │  exception (GlobalExceptionHandler)          │   │
│  └──────────────────────────────────────────────┘   │
│                                                     │
│  ┌──────────────┐   [presence — M7, not yet built]  │
│  │  WebSocket   │   Redis SADD/SREM per chatId      │
│  │  STOMP broker│   GET /chats/{id}/presence        │
│  └──────────────┘                                   │
└────────────────┬────────────────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
  PostgreSQL            Redis
  (3 schemas)        (presence)
```

**Key properties:**
- All modules run in one JVM — cross-module calls are direct method calls.
- `messaging` interacts with `chat` exclusively through `ChatAccessPort` (planned — not yet implemented).
- `UserRegisteredEvent` is synchronous (same thread, same transaction) via `ApplicationEventPublisher`.
- No cross-schema FK constraints — UUID identity convention only.
- STOMP broker is in-process (simple broker); replaced by Kafka in Stage 2.
- In Stage 2, the synchronous `ChatAccessPort` call becomes an HTTP/gRPC call — a known coupling point documented for thesis comparison.

### Stage 1 Module Boundaries

| Rule | Detail |
|---|---|
| `auth` | May not import from `chat`, `messaging`, or `user` |
| `chat` | May not import from `messaging`, `auth`, or `user` |
| `messaging` | Accesses `chat` only through `ChatAccessPort` — no direct repo or entity imports |
| `common` | May not import from any domain module |
| Identity | Flows from JWT principal only — no module queries `user.users` at request time |

### Stage 1 Infrastructure

| Component | Image | Local port | Notes |
|---|---|---|---|
| PostgreSQL | postgres:16-alpine | 5433 | DB `zalord`, 3 schemas |
| PgBouncer | edoburu/pgbouncer | 6433 | transaction mode, pool 25 |
| Redis | redis:7-alpine | 6380 | appendonly yes |
| Spring Boot | custom Dockerfile | 8080 | dev: `./mvnw spring-boot:run` |

---

## Stage 2 — Microservices

## Tech Stack

| Layer | Technology | Rationale |
|---|---|---|
| Go services | Go 1.22+ | Lightweight, high concurrency, ideal for WebSocket and I/O-heavy services |
| Java services | Java 21 + Spring Boot 3 | Strong ecosystem for business logic, Spring Data, validation |
| Message Broker | Apache Kafka | Enables Transactional Outbox pattern; durable, replayable event log |
| Relational DB | PostgreSQL 16 | Users, groups, friendships, group membership, metadata |
| Message DB | ScyllaDB | Time-series message storage; Cassandra-compatible, very high write throughput |
| Cache / Pub-Sub | Redis 7 (AOF enabled) | Sessions, presence, sequence IDs, multi-device session registry, cross-instance WebSocket routing |
| Object Storage | MinIO | Self-hosted S3-compatible storage for media and files |
| Push Notifications | FCM + Web Push (VAPID) | FCM for mobile, VAPID Web Push for browser |
| Inter-service comms | gRPC (internal) + REST (external) | gRPC for performance-critical internal calls; REST for client-facing APIs |
| API Gateway | Nginx | Reverse proxy, SSL termination, rate limiting, routing |
| Deployment | Docker Compose | Single VPS; manageable for a 3-month project |

---

## Service Map

```
                          ┌───────────────────┐
  Client (Web / Mobile) ──▶   Nginx Gateway   │
                          └────────┬──────────┘
                                   │ REST / WebSocket
          ┌───────────────┬────────┼────────┬───────────────┐
          │               │        │        │               │
   ┌──────▼──────┐ ┌──────▼──────┐ │ ┌──────▼──────┐ ┌──────▼──────┐
   │    Auth     │ │    User     │ │ │    Group    │ │    Media    │
   │  Service   │ │  Service   │ │ │  Service   │ │  Service   │
   │ (Java)     │ │  (Go)      │ │ │  (Java)    │ │  (Java)    │
   └─────────────┘ └─────────────┘ │ └─────────────┘ └──────┬──────┘
                                   │                        │ Presigned URL
                            ┌──────▼──────┐            ┌────▼──────┐
                            │    Chat     │            │   MinIO   │
                            │  Service   │            └───────────┘
                            │  (Go+WS)   │
                            └──────┬──────┘
                    ┌──────────────┼──────────────┐
                    │              │              │
             ┌──────▼──────┐  ┌───▼───┐    ┌─────▼─────┐
             │   Postgres  │  │ Redis │    │  Outbox   │
             │  (metadata) │  │ Seq   │    │  Table    │
             └─────────────┘  └───────┘    └─────┬─────┘
                                                  │
                                         ┌────────▼────────┐
                                         │ Message Relay   │
                                         │    (Go)         │
                                         └────────┬────────┘
                                                  │ Kafka publish
                                         ┌────────▼────────┐
                                         │     Kafka       │
                                         └────────┬────────┘
                              ┌───────────────────┼─────────────────┐
                              │                   │                 │
                       ┌──────▼──────┐   ┌────────▼──────┐  ┌──────▼────────┐
                       │    Push     │   │   Message     │  │ Notification  │
                       │  Service   │   │   Service     │  │   Service     │
                       │   (Go+WS)  │   │   (Java)      │  │    (Go)       │
                       └─────────────┘   └───────┬───────┘  └──────┬────────┘
                              │                  │                  │
                       Deliver via          ScyllaDB           FCM Push
                       WebSocket          (messages)
```

---

## Services Overview

| # | Service | Language | Primary Responsibility |
|---|---|---|---|
| 1 | **API Gateway** | Nginx | Routing, SSL, rate limiting, JWT validation pass-through |
| 2 | **Auth Service** | Java / Spring Boot | Register (phone+display name+password), login, JWT, multi-device session management |
| 3 | **User Service** | Go | Profiles, friend requests, presence, privacy settings, read receipts, typing indicators |
| 4 | **Group Service** | Java / Spring Boot | Group CRUD, membership, roles, pinned messages, announcements, admin-only mode |
| 5 | **Chat Service** | Go + WebSocket | Accept connections, receive messages, assign SeqID via Redis, write to Outbox, handle edit/unsend/typing |
| 6 | **Message Relay** | Go | Poll Outbox table, publish confirmed messages to Kafka |
| 7 | **Push Service** | Go + WebSocket | Consume Kafka, fan-out to all active sessions of recipient via Redis |
| 8 | **Message Service** | Java / Spring Boot | Persist to ScyllaDB, history, edit history, soft delete, per-conversation + global search |
| 9 | **Notification Service** | Go | Consume Kafka, send FCM (mobile) + Web Push (browser) for offline users, toggleable content |
| 10 | **Media Service** | Java / Spring Boot | Generate MinIO Presigned URLs, validate file type/size, track metadata |

---

## Communication Patterns

### Synchronous (gRPC / REST)

| Caller | Called | Purpose |
|---|---|---|
| API Gateway | Auth Service | Token validation |
| Chat Service | User Service | Verify sender, get recipient info |
| Chat Service | Group Service | Validate group membership |
| Push Service | User Service | Check online presence before delivery |
| Message Service | User Service | Enrich message history with user profiles |

### Asynchronous (Kafka Topics)

| Topic | Producer | Consumers |
|---|---|---|
| `chat.messages` | Message Relay | Message Service, Push Service, Notification Service |
| `chat.notifications` | Notification Service | (internal fan-out) |
| `media.uploaded` | Media Service | Message Service (attach metadata) |

---

## Core Message Flow

```
1. User A sends a message (WebSocket → Chat Service)
2. Chat Service:
   a. Calls Redis INCR conversation:seq:{conv_id}  → gets SeqID
   b. Opens Postgres transaction:
      - INSERT INTO messages (...)
      - INSERT INTO outbox (status=PENDING, payload=...)
   c. Commits transaction
3. Message Relay:
   a. Polls outbox WHERE status=PENDING
   b. Publishes to Kafka topic `chat.messages`
   c. Updates outbox SET status=PROCESSED
4. Kafka fan-out:
   → Push Service:        looks up ALL active sessions of User B in Redis
                          for each session → delivers via WebSocket (cross-instance via pub/sub)
   → Message Service:     persists to ScyllaDB; if edit/unsend → updates record + stores edit history
   → Notification Service: if User B has NO active sessions → sends FCM (mobile) + Web Push (browser)
```

---

## Multi-Device Delivery

A user can be logged in on multiple devices simultaneously (phone + web browser). Every active device has its own WebSocket connection registered under the user's session registry.

```
Redis key: user:sessions:{user_id}  →  SET of { session_id, instance_id }

Push Service receives message for User B:
    │
    ├── GET user:sessions:{user_b_id}  →  [session_1 (phone), session_2 (browser)]
    │
    ├── session_1 → connected to THIS instance → deliver directly via WebSocket
    │
    └── session_2 → connected to instance_3
            │
            └──► Redis PUBLISH user:delivery:{session_2} {message}
                        │
                        ▼
                 instance_3 delivers to browser WebSocket
```

Notification Service only fires if `user:sessions:{user_b_id}` is empty (no active sessions on any device).

---

## Cross-Instance WebSocket Routing

When Push Service runs as multiple instances, Redis Pub/Sub routes messages to the correct instance per session:

```
Instance 1 needs to deliver to session_2 (on instance 3)
    │
    └──► Redis PUBLISH user:delivery:{session_2} {message}
                │
                ▼
    Instance 3 is subscribed to user:delivery:{session_2}
                │
                ▼
    Instance 3 delivers to that session's WebSocket connection
```

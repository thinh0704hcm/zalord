# Zalord — Design Specification

> **Stage 1 (modular monolith).** This document reflects the current running system.
> Update it when the code changes. Do not let it drift.

---

## 1. Scope

Real-time group and direct chat with phone-number auth, text messages, and online/offline presence.
No file uploads, no typing indicators, no read receipts, no push notifications in Stage 1.

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Spring Boot JVM                    │
│                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │   auth   │  │   user   │  │    messaging     │  │
│  │          │  │          │  │ chats/members/   │  │
│  │ register │  │ User     │  │ messages         │  │
│  │ login    │  │ UserRepo │  │                  │  │
│  └────┬─────┘  └──────────┘  └────────┬─────────┘  │
│       │  UserRegisteredEvent           │            │
│       └───────────────────────────────┘            │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │                  common                      │   │
│  │  security (JWT, CustomUserDetailsService)    │   │
│  │  events (UserRegisteredEvent)                │   │
│  │  exception (GlobalExceptionHandler)          │   │
│  └──────────────────────────────────────────────┘   │
│                                                     │
│  ┌──────────────┐   [presence — M7, not yet built]  │
│  │  WebSocket   │   Redis SADD/SREM per chatId      │
│  │  STOMP broker│   GET /chats/{id}/presence        │
│  └──────────────┘                                   │
└────────────────────┬────────────────────────────────┘
                     │
          ┌──────────┴──────────┐
          │                     │
    PostgreSQL               Redis
    (3 schemas)           (presence)
```

**Key properties of the monolith shape:**
- All modules run in one JVM; cross-module calls are direct method calls.
- Cross-module state changes use `ApplicationEventPublisher` (synchronous, same transaction by default).
- No cross-schema FK constraints — UUID identity convention only.
- STOMP broker is in-process (simple broker); no external message broker.

---

## 3. Modules

### 3.1 `auth`

**Owns:** `auth.credentials` table, JWT issuance.

**Exposes:** `POST /api/auth/register`, `POST /api/auth/login`

**Publishes:** `UserRegisteredEvent` (via `ApplicationEventPublisher`) after registration.

**Consumes:** nothing from other modules directly.

**Notable coupling:** `common.security.CustomUserDetailsService` reads `auth.CredentialRepository` to load security principal. This is an intentional coupling inside `common` — documented in [ADR-auth](docs/adr/adr-auth.md).

### 3.2 `user`

**Owns:** `user.users` table, `User` entity.

**Exposes:** no REST endpoints in Stage 1.

**Consumes:** listens for `UserRegisteredEvent` to create a `User` row (not yet implemented — M7/M5 discovery item).

**Notable coupling:** `messaging.messages.sender_id` references `user.users.id` by UUID convention only — no FK.

### 3.3 `messaging`

**Owns:** `messaging.chats`, `messaging.chat_members`, `messaging.messages` tables.

**Exposes:**
- `POST /api/chats` — create chat
- `GET /api/chats` — list chats for current user
- `PATCH /api/chats/{id}` — update chat name
- `DELETE /api/chats/{id}` — delete chat (owner only)
- `POST /api/chats/{id}/members` — add member
- `DELETE /api/chats/{id}/members/{memberId}` — remove member / leave
- `PATCH /api/chats/{id}/members/{memberId}/role` — promote/demote
- `GET /api/chats/{id}/messages?cursor=&limit=` — paginated history (M3)
- `@MessageMapping /app/chat/{id}/send` — STOMP send (M4)

**Key transactional boundary:** `ChatService.createChat` writes to both `chats` and `chat_members` in one `@Transactional`. `ChatService.leaveChat` has owner-succession logic (promotes next admin or oldest member) — also one transaction. This is the primary consistency guarantee Stage 2 must replicate or explicitly sacrifice.

**Consumes:** nothing from other modules at runtime (sender identity comes from the JWT principal).

### 3.4 `common`

**Owns:** no database tables.

**Contains:**
- `security/` — `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `SecurityConfig`, `PasswordEncoderConfig`, `AuthenticatedUser`
- `events/` — `UserRegisteredEvent`
- `exception/` — `GlobalExceptionHandler`, domain exception classes

**Notable coupling:** `CustomUserDetailsService` reaches into `auth.CredentialRepository` directly. This is the primary boundary violation to document in M5 and extract in Stage 2.

### 3.5 `presence` _(M7 — not yet built)_

**Will own:** Redis keys `presence:chat:{chatId}` (set of online user IDs).

**Will expose:** `GET /api/chats/{chatId}/presence`

**Will consume:** Spring WebSocket connect/disconnect events via `ApplicationEventPublisher`.

**Key property:** In the monolith, presence is trivially derived from in-JVM WebSocket session state. This is the sharpest contrast point with Stage 2, where presence requires broker-mediated events.

---

## 4. Database Schema

Single PostgreSQL database `zalord`. Three schemas with no cross-schema FK constraints.

```sql
-- Schema: user
user.users (id uuid PK, full_name, email, phone_number UNIQUE, birth_date, gender, created_at, deleted_at)

-- Schema: auth
auth.credentials (id uuid PK, user_id uuid UNIQUE, phone_number UNIQUE, email, password_hash, is_active, last_login)
-- user_id → user.users.id  [UUID convention, no FK]

-- Schema: messaging
messaging.chats (id uuid PK, chat_name, chat_type CHECK('DIRECT','GROUP','COMMUNITY'), last_activity_at, created_at, deleted_at)
messaging.messages (id uuid PK, chat_id uuid, sender_id uuid, content_type CHECK('TEXT','IMAGE','VIDEO','FILE'), payload jsonb, created_at, deleted_at)
messaging.chat_members (chat_id uuid, member_id uuid, role CHECK('MEMBER','ADMIN','OWNER'), deleted_at, PK(chat_id,member_id))

-- Indexes
messages_chat_id_idx ON messaging.messages(chat_id, created_at DESC)  -- used by cursor-based history
chat_members_member_id_idx ON messaging.chat_members(member_id)        -- used by membership checks
```

**Soft deletes:** `deleted_at` column on `users`, `chats`, `messages`. Queries must filter `deleted_at IS NULL`.

**Content-type constraint:** Only `TEXT` is used in Stage 1. Other types exist in the schema as stubs.

---

## 5. API

### 5.1 Auth

```
POST /api/auth/register
  Body: { phoneNumber, password, fullName }
  201: { token, expiresIn }

POST /api/auth/login
  Body: { phoneNumber, password }
  200: { token, expiresIn }
```

JWT is a Bearer token. No refresh token in Stage 1 (single-token, configurable expiry via `JWT_EXPIRY_MINUTES`).
All endpoints except auth require `Authorization: Bearer <token>`.

### 5.2 Messaging REST

```
POST   /api/chats                           create chat
GET    /api/chats                           list my chats
PATCH  /api/chats/{id}                      rename (ADMIN+)
DELETE /api/chats/{id}                      delete (OWNER only)
POST   /api/chats/{id}/members              add member (ADMIN+)
DELETE /api/chats/{id}/members/{memberId}   remove / leave
PATCH  /api/chats/{id}/members/{memberId}/role  promote/demote (OWNER/ADMIN)
GET    /api/chats/{id}/messages?cursor=&limit=  paginated history (M3)
```

Cursor-based pagination: `cursor` = `createdAt` of last seen message (ISO-8601), `limit` max 50.

### 5.3 Presence (M7)

```
GET /api/chats/{chatId}/presence  → [ userId, … ]
```

### 5.4 WebSocket / STOMP (M4)

```
Handshake: ws://host/ws  (JWT via Authorization header or ?token= query param)
Subscribe:  /topic/chats/{chatId}
Send:       /app/chat/{chatId}/send   { contentType, payload }
```

Unauthenticated handshake → rejected (401). Message persisted to DB before broadcast.

---

## 6. Cross-Module Event Flow

```
AuthService.register()
  └── ApplicationEventPublisher.publishEvent(UserRegisteredEvent)
        └── [listener in user module — M5 discovery item: does this exist?]
```

All events in Stage 1 are **synchronous** (same thread, same transaction unless `@Async`). This means `UserRegisteredEvent` listeners are part of the registration transaction — a fact to document in M5 ADRs.

In Stage 2, `ApplicationEventPublisher` is replaced by RabbitMQ. This is the primary architectural shift.

---

## 7. Security

- Spring Security filter chain.
- `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`.
- `CustomUserDetailsService.loadUserByUsername(phoneNumber)` calls `CredentialRepository` directly.
- `AuthenticatedUser` wraps the Spring `UserDetails` principal; injected via `@AuthenticationPrincipal` in controllers.
- WebSocket handshake auth: JWT validated in `ChannelInterceptor` or `HandshakeInterceptor` (M4 implementation detail).

---

## 8. Infrastructure

| Component | Image | Local port | Notes |
|---|---|---|---|
| PostgreSQL | postgres:16-alpine | 5433 | DB `zalord`, user `zalord_user` |
| PgBouncer | edoburu/pgbouncer | 6433 | transaction mode, pool 25 |
| Redis | redis:7-alpine | 6380 | appendonly yes |
| Spring Boot | custom Dockerfile | 8080 | dev: `./mvnw spring-boot:run` |

---

## 9. Stage 2 Extraction Preview

This section is intentionally thin — Stage 2 is not planned until M5 ADRs exist.

Likely first extraction candidate: `auth` (cleanest boundary — only internal dependency is `common.security`).

Most interesting extraction: `presence` (stateful; in monolith it uses in-JVM session state; in Stage 2 it requires a broker).

Key open question (to be answered by M5): Does `user` module currently listen to `UserRegisteredEvent`? If not, `user.users` rows are never populated, and `messaging.messages.sender_id` references a table that has no rows — a silent bug to confirm during M5 audit.

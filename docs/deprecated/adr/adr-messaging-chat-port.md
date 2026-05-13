# ADR: ChatAccessPort — messaging → chat boundary

**Status:** Accepted
**Date:** 2026-04-13

---

## Context

After separating `chat` (chat management) and `messaging` (message send/delete/history) into distinct modules, `MessageService` needed to check chat membership and update `lastActivityAt` before persisting a message.

The naive approach was to inject `ChatRepository` and `ChatMemberRepository` directly into `MessageService`. This was the original implementation and created a hard cross-module dependency: `messaging` imported entities, enums, and repositories from `chat`.

This violated the modular boundary. If `chat` and `messaging` were extracted into separate microservices, `messaging` would need direct database access to `chat`'s tables — impossible across service boundaries.

---

## Decision

Introduce a **port** in the `messaging` module defining what it needs from `chat`, and an **adapter** in the `chat` module implementing that port.

```
io.zalord.messaging.port.ChatAccessPort     ← interface, owned by messaging
io.zalord.chat.adapter.ChatAccessAdapter    ← implementation, lives in chat
```

The port exposes two methods:

```java
boolean canSendMessage(UUID chatId, UUID actorId);
void updateLastActivity(UUID chatId, Instant timestamp);
```

`canSendMessage` encapsulates all business rules about who can send (membership check + COMMUNITY role gate). `messaging` receives a boolean and throws its own `UnauthorizedException` — it never sees `ChatMemberRole` or `ChatType`.

`updateLastActivity` is a synchronous in-memory call in Stage 1.

---

## Consequences

**Positive:**
- `messaging` has zero imports from the `chat` package.
- Business rules about chat access live entirely in `chat`.
- Each module throws its own exceptions — no exception leakage across boundaries.
- Dependency arrow: `chat` depends on `messaging.port`, not the reverse.

**Negative / trade-offs:**
- `chat` now imports `messaging.port` to implement the interface. This is a compile-time dependency from `chat` to `messaging` — acceptable because the interface is minimal and stable.

---

## Stage 2 implications (thesis note)

In Stage 1, `ChatAccessAdapter` calls repos directly — a fast, in-memory, transactional call. The entire `sendMessage` flow (permission check + message persist + lastActivity update) runs in one `@Transactional`.

In Stage 2, `messaging` becomes its own service. The adapter is **replaced** by an HTTP/gRPC client pointing at chat-service. This creates a synchronous network call: if chat-service is down, `messaging` cannot complete `sendMessage`. This is the deliberate coupling point documented for architectural comparison.

The planned Stage 2 resolution: replace `updateLastActivity` with an async `MessageSent` domain event consumed by chat-service — decoupling availability.

# ADR — `presence` Module Boundary

> **Status:** Draft (M5 — fill in during boundary audit; module not yet built — see M7)
> **Author:** _your name_
> **Date:** _YYYY-MM-DD_

---

## What does this module own?

_(To be built in M7. Fill in actuals after implementation.)_

- [ ] Redis keys: `presence:chat:{chatId}` (set of online member UUIDs)
- [ ] `GET /api/chats/{chatId}/presence` endpoint
- [ ] WebSocket connect/disconnect event listeners
- [ ] Online/offline state transitions

---

## What does it consume from other modules?

| Dependency | Source module | How |
|---|---|---|
| WebSocket session connect event | Spring WebSocket / `common`? | `ApplicationEventPublisher` or `@EventListener` |
| WebSocket session disconnect event | Spring WebSocket / `common`? | Same |
| Chat membership (to validate who can query presence) | `messaging` | _TBD: direct call or duplicate check?_ |
| | | |

**Questions to answer during M7 implementation and M5 audit:**
- Where do WebSocket connect/disconnect events originate? Is there a `ChannelInterceptor` in `common` or in `messaging`? Which module should own this?
- Does the presence endpoint need to verify that the requesting user is a member of the chat? If yes, does it call `messaging.ChatMemberRepository` directly, or does it duplicate the check?
- How does the presence module know which `chatId` a WebSocket session belongs to? Does the STOMP subscription destination encode this, or does the session carry it as metadata?

---

## Monolith vs. Stage 2 — the key contrast

**In the monolith (Stage 1):**
- WebSocket sessions are in-JVM objects managed by Spring's `WebSocketSession` registry.
- Online/offline state can be derived directly from in-memory session state without Redis.
- Redis is used for durability across app restarts and to support `GET /presence` without scanning JVM memory.
- `SADD`/`SREM` happen synchronously in the connect/disconnect handler — no eventual consistency.

**In Stage 2 (microservices):**
- The presence service runs in a separate JVM with no access to the messaging service's WebSocket sessions.
- Connect/disconnect events must flow through a message broker (RabbitMQ).
- This introduces latency and at-least-once delivery semantics — a user may briefly appear online after disconnect if the broker event is delayed.
- This is the sharpest architectural contrast in the comparison study.

Document both sides explicitly when Stage 1 is done, so the comparison report can cite specific differences.

---

## Known limitations to document (M7)

- Spring WebSocket disconnect events fire unreliably on unclean disconnects (browser tab close, network drop). Document the cases where a user stays "online" in Redis past actual disconnect.
- TTL strategy: Redis key cleanup on disconnect. What is the TTL for stale entries? Is it 60 seconds? Document the chosen value and why.
- This limitation exists equally in Stage 2 — note it as a comparison data point, not a monolith-specific flaw.

---

## Likely extraction point for Stage 2

- Presence service owns: Redis state, online/offline transitions, `GET /presence` endpoint.
- Main obstacle: needs a broker subscription to receive connect/disconnect events from the messaging service.
- Risk level: _HIGH_ — stateful module, at-least-once delivery semantics, unreliable disconnect events.

---

## Notes

_Fill in after M7 is built: what did you discover that wasn't anticipated?_


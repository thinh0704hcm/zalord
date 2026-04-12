# ADR — `messaging` Module Boundary

> **Status:** Draft (M5 — fill in during boundary audit)
> **Author:** _your name_
> **Date:** _YYYY-MM-DD_

---

## What does this module own?

- [ ] `messaging.chats` table
- [ ] `messaging.chat_members` table
- [ ] `messaging.messages` table
- [ ] `ChatService` — chat lifecycle and membership management
- [ ] `MessageService` — message send and history
- [ ] STOMP `@MessageMapping` handlers (M4)
- [ ] REST controllers for chats and messages

---

## What does it consume from other modules?

| Dependency | Source module | How |
|---|---|---|
| Authenticated user identity (UUID) | `common.security.AuthenticatedUser` | `@AuthenticationPrincipal` in controllers |
| | | |

**Questions to answer during audit:**
- Does `messaging` import anything from `auth` directly? (Expected: no — identity comes from the JWT principal, not from `auth.CredentialRepository`)
- Does `messaging` import anything from `user.UserRepository`? (Expected: no in Stage 1, but check)
- Does `MessageService.sendMessage` enrich responses with the sender's display name? If yes, it touches `user` — document the import.

```bash
grep -r "UserRepository\|CredentialRepository" backend/src/main/java/io/zalord/messaging/
```

---

## Transactional consistency — key comparison data point

Document every multi-table write that happens in a single `@Transactional`:

| Method | Tables written | What breaks if split across services? |
|---|---|---|
| `ChatService.createChat` | `chats` + `chat_members` | Chat exists with no owner — orphan state |
| `ChatService.leaveChat` (owner path) | `chat_members` (delete) + `chat_members` (promote successor) | Chat exists with no owner between two writes |
| `ChatService.deleteChat` | `chats` (soft-delete) + ? | Audit: does it also touch `chat_members`? |
| `MessageService.sendMessage` | `messages` + `chats.last_activity_at` | Message persisted but chat timestamp stale — acceptable inconsistency? |

This table is the primary input to the Stage 2 consistency trade-off analysis.

---

## Known coupling points

- `chat_members.member_id` → identity comes from JWT principal. No import from `user` at runtime in Stage 1. **Confirm during audit.**
- `messages.sender_id` → same as above.
- STOMP broadcast happens in the same JVM after `MessageService.sendMessage`. In Stage 2, broadcast must go through a broker — document the sequencing constraint (persist-before-broadcast).

---

## Likely extraction point for Stage 2

_If `messaging` were split into `chat-service` + `message-service`:_

- `chat-service` owns: chat lifecycle, membership, roles.
- `message-service` owns: send, history, STOMP delivery.
- Main obstacle: `createChat` + `sendMessage` each span multiple tables. Splitting services requires either saga/2PC or accepting partial failure states. Document which consistency model you would choose and why.
- Risk level: _LOW / MEDIUM / HIGH_ — justify.

---

## Notes

_Anything surprising found during the audit. N+1 queries in history endpoint? Unexpected imports?_


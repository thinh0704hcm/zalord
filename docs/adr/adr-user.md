# ADR — `user` Module Boundary

> **Status:** Draft (M5 — fill in during boundary audit)
> **Author:** _your name_
> **Date:** _YYYY-MM-DD_

---

## What does this module own?

- [ ] `user.users` table
- [ ] `User` entity
- [ ] `UserRepository`
- [ ] _(any other responsibilities?)_

---

## What does it consume from other modules?

| Dependency | Source module | How |
|---|---|---|
| `UserRegisteredEvent` | `common.events` | event listener (if listener exists) |
| | | |

**Critical question to answer during audit:**

Does a `UserRegisteredEvent` listener exist in this module? Grep for `@EventListener` or `@TransactionalEventListener` in `user/`.

```bash
grep -r "EventListener\|UserRegisteredEvent" backend/src/main/java/io/zalord/user/
```

If no listener exists: `user.users` table is never populated by registration. Then `messaging.messages.sender_id` references IDs that have no corresponding row in `user.users`. **Document this as a known bug or known gap.**

---

## Known coupling points

- `messaging.messages.sender_id` → `user.users.id` by UUID convention (no FK constraint). Document whether this is intentional (loose coupling for future service extraction) or accidental.
- `messaging.chat_members.member_id` → `user.users.id` by UUID convention. Same question.

---

## Likely extraction point for Stage 2

_Describe where the boundary would be drawn if `user` became a separate service._

- User service owns: user profile reads/writes, profile enrichment for display names.
- Main obstacle: messaging service needs sender display names for message history responses (currently deferred — M3 explicitly excludes sender name enrichment). In Stage 2, this becomes a cross-service call.
- Risk level: _LOW / MEDIUM / HIGH_ — justify.

---

## Notes

_Anything surprising found during the audit._


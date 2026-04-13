# ADR: No cross-schema foreign key constraints

**Status:** Accepted
**Date:** 2026-04-13

---

## Context

The database has three schemas: `user`, `auth`, `messaging`. Multiple relationships exist across them:

- `auth.credentials.user_id` → `user.users.id`
- `messaging.messages.sender_id` → `user.users.id`
- `messaging.chat_members.member_id` → `user.users.id`

The straightforward approach is to declare PostgreSQL `FOREIGN KEY` constraints across schemas. PostgreSQL supports this.

---

## Decision

No cross-schema FK constraints. References are by UUID convention only — the application code is responsible for consistency.

---

## Rationale

**Microservice extraction:** Each schema maps to a future microservice boundary. If cross-schema FKs existed, dropping or extracting a schema would require dropping or migrating constraints — a painful, risky operation. With UUID convention, each schema (and future service) is self-contained at the database level.

**No cross-schema JOINs:** Services should never JOIN across module boundaries. If a query needs data from two schemas, it is a sign that the query belongs to the wrong module. Removing FKs makes this boundary violation immediately visible — the query fails rather than silently succeeding.

**Simpler migrations:** Flyway migrations per schema are independent. A migration in `messaging` schema does not block a migration in `user` schema.

---

## Consequences

**Positive:**
- Schema independence — each can be moved to a separate database in Stage 2 with no FK migration work.
- Extraction is a matter of pointing a service at a new database, not restructuring constraints.

**Negative:**
- No database-level referential integrity across schemas. A bug that deletes a user without cleaning up their messages or credentials is not caught by the DB.
- Application code must enforce consistency — particularly the `UserRegisteredEvent` listener that creates `user.users` rows when `auth.credentials` rows are created.

---

## Consistency mechanism

The `UserRegisteredEvent` (synchronous, same transaction as `AuthService.register`) ensures that `user.users` and `auth.credentials` are always created together. If the listener fails, the entire registration transaction rolls back.

`messaging.messages.sender_id` and `messaging.chat_members.member_id` reference users who were created through this mechanism — consistency is maintained at the application layer, not the DB layer.

# Architecture Decision Records

One file per module. Fill in during M5 (boundary audit).

| File | Module | Status |
|---|---|---|
| [adr-auth.md](adr-auth.md) | `auth` | Draft |
| [adr-user.md](adr-user.md) | `user` | Draft |
| [adr-messaging.md](adr-messaging.md) | `messaging` | Draft |
| [adr-presence.md](adr-presence.md) | `presence` | Draft (module not yet built — M7) |
| [adr-common.md](adr-common.md) | `common` | Draft |

Each ADR answers:
1. What does this module own?
2. What does it consume from other modules, and how?
3. What are the known coupling points?
4. Where is the likely extraction boundary for Stage 2, and what is the main obstacle?

Exit gate for M5: all five files are filled in (not Draft) and committed.

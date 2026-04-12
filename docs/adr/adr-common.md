# ADR — `common` Module Boundary

> **Status:** Draft (M5 — fill in during boundary audit)
> **Author:** _your name_
> **Date:** _YYYY-MM-DD_

---

## What does this module own?

`common` is a shared library module — it owns no domain state but provides cross-cutting infrastructure.

- [ ] `security/` — JWT validation, Spring Security filter chain, user principal
- [ ] `events/` — event DTOs shared across modules (`UserRegisteredEvent`)
- [ ] `exception/` — domain exception classes, `GlobalExceptionHandler`

---

## What does it consume from other modules?

| Dependency | Source module | How |
|---|---|---|
| `auth.CredentialRepository` | `auth` | Direct Spring Data repository injection in `CustomUserDetailsService` |
| | | |

**This is the primary boundary violation to document.**

`CustomUserDetailsService` is in `common.security` but it reads from `auth.CredentialRepository`. This means:
- `common` has a compile-time dependency on `auth`.
- If `auth` is extracted as a separate service, `common.security` breaks — it can no longer inject the repository.
- The extraction strategy must move credential lookup into `auth` and expose a narrow interface (e.g., `POST /auth/validate-token` or a shared JWT library).

---

## Questions to answer during audit

- Does any module other than `auth` publish `UserRegisteredEvent`? (Expected: no)
- Does any module other than `user` listen to `UserRegisteredEvent`? (Expected: no — but confirm)
- Does `GlobalExceptionHandler` import from any specific module? (Expected: no — it handles generic exception types)
- Does `SecurityConfig` import anything from `messaging`? (Expected: no)

```bash
# Check all imports in common
grep -r "^import io.zalord\." backend/src/main/java/io/zalord/common/
```

---

## Role in Stage 2

In Stage 2, `common` splits into:
- A **shared library** (JAR) containing: event DTOs, exception contracts, JWT utilities used by all services.
- The JWT filter moves into each service that needs it (or a shared security library).
- `CustomUserDetailsService` is retired — each service validates JWT claims directly using the shared JWT library.

Document: what is the minimal set of classes that must move into a shared library for Stage 2 to compile?

---

## Notes

_Anything surprising found during the audit. Unexpected imports? Spring beans that cross module lines?_


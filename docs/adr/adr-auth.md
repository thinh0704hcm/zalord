# ADR — `auth` Module Boundary

> **Status:** Draft (M5 — fill in during boundary audit)
> **Author:** _your name_
> **Date:** _YYYY-MM-DD_

---

## What does this module own?

_List the tables, entities, and responsibilities that belong exclusively to `auth`._

- [ ] `auth.credentials` table
- [ ] JWT token issuance (`JwtService`)
- [ ] Phone-number + password validation
- [ ] `UserRegisteredEvent` publication

---

## What does it consume from other modules?

For each dependency, record: **what** is consumed, **from which module**, and **how** (direct import / shared table / event).

| Dependency | Source module | How |
|---|---|---|
| _e.g. `UserRepository`_ | _`user`_ | _direct import_ |
| | | |

**Questions to answer during audit:**
- Does `auth` import anything from `messaging`? (Expected: no)
- Does `auth` import anything from `user`? (Expected: one-way — `auth` publishes `UserRegisteredEvent`, `user` listens)

---

## Known coupling points

**`common.security.CustomUserDetailsService` → `auth.CredentialRepository`**

This is the primary known coupling: the security layer (in `common`) reaches directly into `auth`'s repository to load credentials for JWT validation. Document:
- Is this coupling intentional? Why is it in `common` rather than `auth`?
- What would break if `auth` were extracted as a service? (Answer: `CustomUserDetailsService` cannot call a repository directly — it would need an HTTP call or a shared token-validation endpoint.)
- What is the extraction strategy? (Options: move auth validation into `auth` module and expose a narrow interface; or keep a token-validation endpoint in the auth service that `common` calls via HTTP.)

---

## Likely extraction point for Stage 2

_Describe where the boundary would be drawn if `auth` became a separate service._

- Auth service owns: registration, login, token issuance, token validation endpoint.
- Main obstacle: `common.security` must be rewritten to call the auth service's token validation endpoint instead of reading `CredentialRepository` directly.
- Risk level: _LOW / MEDIUM / HIGH_ — justify.

---

## Notes

_Anything surprising found during the audit. Cross-module imports, shared tables, assumptions that turned out to be wrong._


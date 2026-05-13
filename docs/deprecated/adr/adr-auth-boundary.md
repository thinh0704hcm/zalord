# ADR: Auth module boundary — CustomUserDetailsService

**Status:** Accepted
**Date:** 2026-04-13

---

## Context

Spring Security requires a `UserDetailsService` implementation to load a user principal during JWT authentication. This class needs to look up credentials by phone number to verify identity.

The original implementation placed `CustomUserDetailsService` in `common.security`. This created a direct import: `common` → `auth.repository.CredentialRepository`. `common` is supposed to be a shared module with no domain dependencies — this violated that rule.

---

## Decision

Move `CustomUserDetailsService` into the `auth` module (`io.zalord.auth.application.CustomUserDetailsService`).

`auth` owns credentials and JWT issuance. Loading a user by phone number for Spring Security is an auth concern — it belongs in `auth`, not in a shared module.

`common.security` retains only infrastructure: `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, `PasswordEncoderConfig`, `AuthenticatedUser`. None of these depend on any domain module.

---

## Consequences

**Positive:**
- `common` has zero imports from any domain module — boundary is clean.
- `auth` owns all credential-related logic in one place.
- Easier to extract `auth` as a microservice in Stage 2 — no shared module carries its internal state.

**Negative:**
- `SecurityConfig` (in `common`) must still reference `CustomUserDetailsService`. Since Spring wires this by type at runtime via the application context, there is no compile-time import required — Spring's DI handles it.

---

## Stage 2 implications

When `auth` becomes its own microservice, JWT validation moves to the API Gateway (Nginx or a gateway service). Downstream services validate token signature only — they never call `CustomUserDetailsService`. The class is no longer needed outside `auth-service`.

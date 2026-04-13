# M5 — ADR Boundary Audit Summary

Captured: 2026-04-13

## Completed Items

- [x] Fix: UserEventListener created — UserRegisteredEvent now populates user.users in the same transaction as auth.credentials
- [x] Implement: ChatAccessPort interface in messaging module + ChatAccessAdapter in chat module (dependency inversion — messaging defines the contract, chat implements it)
- [x] Document: messaging→chat coupling decision — see docs/adr/adr-messaging-chat-port.md
- [x] Document: common→auth coupling history (CustomUserDetailsService moved to auth module) — see docs/adr/adr-auth-boundary.md
- [x] Document: no cross-schema FK constraints rationale — see docs/adr/adr-no-cross-schema-fk.md

## ADR Files
- docs/adr/adr-messaging-chat-port.md
- docs/adr/adr-auth-boundary.md
- docs/adr/adr-no-cross-schema-fk.md

## Result
All coupling violations are either resolved or deliberately accepted with documented rationale. Stage 1 boundary audit complete.

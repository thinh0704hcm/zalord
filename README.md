# zalord

A real-time chat system (1-on-1 + group messaging, WebSocket delivery, presence, read receipts, media sharing, push notifications) built as a microservices learning project.

> Status: **Sprint 1 / Foundation** — repo skeleton and infra coming up. Most services are placeholders. This README will grow as features land.

## Tech stack

- **Java 21 / Spring Boot 4** — `auth-service`, `group-service`, `message-service`, `media-service`
- **Go 1.22** — `user-service`, `chat-service`, `push-service`, `message-relay`, `notification-service`
- **PostgreSQL 16** — relational data (users, groups, friendships, outbox)
- **ScyllaDB** — message history (canonical store)
- **Redis 7** — sessions, presence, per-conversation sequence IDs, pub/sub
- **Apache Kafka** — async event bus (Transactional Outbox pattern)
- **MinIO** — S3-compatible media storage
- **Nginx** — API gateway, WebSocket termination
- **Docker Compose** — local + single-VPS deployment

## Repository layout

```
backend/      One directory per microservice
infra/        Container init scripts (Postgres / Scylla / Kafka / MinIO / Redis)
nginx/        API gateway config
shared/       Cross-service code (Go JWT verifier, proto stubs)
scripts/      Ops + smoke-test scripts
deploy/       Kubernetes manifests (later)
frontend/     React + Vite client (placeholder)
docs/         Design docs — architecture, services, database, patterns
```

## Quick start

Requires Docker (Desktop on macOS/Windows, native on Linux) and `make`. Windows users: run `make` from Git Bash or WSL.

```bash
cp .env.example .env       # fill in secrets
make dev                   # start the full stack
make dev-status            # check container health
make smoke                 # end-to-end acceptance test (once Sprint 1 is done)
make help                  # everything else
```

## Docs

See [`docs/`](./docs) for the full design:

- [`architecture.md`](./docs/architecture.md) — service map, tech choices, communication patterns
- [`services.md`](./docs/services.md) — per-service responsibilities and API surface
- [`database.md`](./docs/database.md) — PostgreSQL + ScyllaDB schemas, Redis keys
- [`patterns.md`](./docs/patterns.md) — Transactional Outbox, Redis sequence, Presigned URL
- [`infrastructure.md`](./docs/infrastructure.md) — deployment, RAM budget, compose layout

## License

TBD.

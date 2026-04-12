# Zalord — Development Guide

> Covers: local setup, running the app, running tests, working conventions.
> For architecture, see [DESIGN.md](DESIGN.md). For milestone plans, see [bad-ai-docs/STAGE_1_PLAN.md](bad-ai-docs/STAGE_1_PLAN.md).

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker + Docker Compose | 24+ | Required for Postgres, Redis, PgBouncer |
| Java | 21 | `JAVA_HOME` must point to JDK 21 |
| Maven wrapper | included | Use `./mvnw`, not a system `mvn` |
| `wscat` | any | `npm i -g wscat` — used for WebSocket verification |
| `curl` | any | Used for evidence collection |

---

## 1. First-Time Setup

```bash
# 1. Clone and enter the repo
git clone <repo-url> zalord && cd zalord

# 2. Copy and fill in environment variables
cp .env.example .env
# Edit .env — set DB_PASSWORD and JWT_SECRET (min 32 chars)

# 3. Start infrastructure
docker compose up -d

# 4. Verify Postgres is ready
docker compose ps   # postgres should show "healthy"

# 5. Run the backend
cd backend
./mvnw spring-boot:run
# First run downloads dependencies (~2 min on cold cache)
```

The app listens on `http://localhost:8080`.

---

## 2. Environment Variables

All variables live in `.env` at the repo root. The `springboot4-dotenv` library loads them automatically — no export needed.

| Variable | Example | Required |
|---|---|---|
| `DB_PASSWORD` | `s3cr3t` | Yes |
| `JWT_SECRET` | `at-least-32-characters-long-secret` | Yes |
| `JWT_EXPIRY_MINUTES` | `60` | No (default: 15) |

> `JWT_REFRESH_EXPIRY_DAYS` appears in `.env.example` but is not used — no refresh token in Stage 1.

---

## 3. Schema / Migrations

**Current state (pre-M1):** Schema is applied from `infrastructure/postgres/init.sql` via Docker volume mount on first container start.

**After M1 (Flyway):** The `init.sql` mount will be replaced by Flyway. Run `./mvnw flyway:info` to verify migration state.

To reset the database during development:
```bash
docker compose down -v   # drops volumes — destroys all data
docker compose up -d     # re-creates from scratch
```

---

## 4. Running Tests

```bash
# All tests
cd backend && ./mvnw test

# Unit tests only (messaging, tagged — after M2)
./mvnw test -Dgroups=unit-messaging

# Skip tests for a fast build
./mvnw spring-boot:run -DskipTests
```

Tests use `spring-boot-starter-data-jpa-test` and `spring-boot-starter-data-redis-test`.
Integration tests that need a live DB/Redis require Docker to be running.

---

## 5. Verifying WebSocket (M4 evidence)

```bash
# Terminal 1 — subscribe to a chat
wscat -c "ws://localhost:8080/ws" \
  -H "Authorization: Bearer <token>"
# After connect, subscribe:
# > ["SUBSCRIBE", {"id":"sub-0","destination":"/topic/chats/<chatId>"}]

# Terminal 2 — send a message
wscat -c "ws://localhost:8080/ws" \
  -H "Authorization: Bearer <token>"
# > ["SEND", {"destination":"/app/chat/<chatId>/send"}, {"contentType":"TEXT","payload":{"text":"hello"}}]
```

Terminal 1 should receive the message. Confirm persistence:
```bash
docker exec -it zalord-postgres-1 psql -U zalord_user -d zalord \
  -c "SELECT id, sender_id, payload, created_at FROM messaging.messages ORDER BY created_at DESC LIMIT 5;"
```

---

## 6. Verifying Presence (M7 evidence)

```bash
# Check who is online in a chat
curl -s -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/chats/<chatId>/presence | jq

# Check Redis directly
docker exec -it zalord-redis-1 redis-cli SMEMBERS "presence:chat:<chatId>"
```

---

## 7. Actuator / Metrics (M6)

```bash
# Health
curl http://localhost:8080/actuator/health

# All metric names
curl http://localhost:8080/actuator/metrics

# Specific metric (HTTP request latency)
curl "http://localhost:8080/actuator/metrics/http.server.requests"
```

Prometheus scrape endpoint: `http://localhost:8080/actuator/prometheus`

---

## 8. Module Structure

```
backend/src/main/java/io/zalord/
├── ZalordApplication.java
├── auth/                        # phone-number auth, JWT issuance
│   ├── CredentialRepository.java
│   ├── api/controller/
│   ├── application/AuthService.java
│   ├── commands/
│   ├── dto/
│   └── model/Credential.java
├── user/                        # user profile
│   ├── User.java
│   └── UserRepository.java
├── messaging/                   # chats, members, messages
│   ├── api/dto/
│   ├── application/
│   │   ├── ChatService.java
│   │   └── MessageService.java
│   ├── domain/
│   │   ├── entities/
│   │   └── enums/
│   └── infrastructure/          # Spring Data repositories
└── common/                      # shared: security, events, exceptions
    ├── events/UserRegisteredEvent.java
    ├── exception/
    └── security/
```

Cross-module rules (enforced by ADRs, not code):
- `auth` may not import from `messaging` or `user`.
- `messaging` may not import from `auth` or `user` (use JWT principal for identity).
- `common` may import from `auth` only for `CredentialRepository` (known coupling — see [ADR-auth](docs/adr/adr-auth.md)).
- No module may import from `common.security` internals except Spring itself.

---

## 9. Conventions

### Naming
- REST controllers: `XxxController` in `<module>/api/controller/`
- Service layer: `XxxService` in `<module>/application/`
- Repositories: `XxxRepository` in `<module>/infrastructure/`
- Domain entities: in `<module>/domain/entities/`

### Transactions
- `@Transactional` lives on service methods, never on controllers or repositories.
- Multi-table writes (e.g., `createChat` writing to both `chats` and `chat_members`) must be in one service method with one `@Transactional`.

### DTOs
- Request DTOs: validated with `@Valid` + Bean Validation annotations.
- Response DTOs: plain records or `@Value`-annotated classes. No entity leakage to controllers.

### Error handling
- All exceptions thrown as subtypes of the domain exceptions in `common.exception/`.
- `GlobalExceptionHandler` maps them to consistent JSON: `{ error, message, timestamp }`.

### Soft deletes
- Entities with `deleted_at` must never be hard-deleted in Stage 1.
- Repository queries must include `WHERE deleted_at IS NULL` (or use a `@Where` annotation).

---

## 10. Evidence Collection Checklist

Collect these as you complete each milestone. Save files under `docs/evidence/`.

| Milestone | File | Contents |
|---|---|---|
| M1 | `docs/evidence/M1-flyway-info.txt` | `flyway:info` output showing V1 applied |
| M2 | `docs/evidence/M2-test-output.txt` | `./mvnw test` output showing 10+ messaging cases green |
| M3 | `docs/evidence/M3-message-history.sh` | `curl` transcript: first page, 403 non-member, empty chat |
| M4 | `docs/evidence/M4-wscat-transcript.txt` | `wscat` showing two clients exchanging a message |
| M6 | `docs/evidence/M6-baseline-metrics.md` | startup time, p50/p99, DB query count per request |
| M7 | `docs/evidence/M7-presence-transcript.txt` | connect → presence update → disconnect → Redis SMEMBERS cleared |

---

## 11. Making the Local Startup Repeatable

The Stage 1 exit gate requires `docker compose up && ./mvnw spring-boot:run` to work from scratch.

Checklist before calling it done:
- [ ] `docker compose down -v && docker compose up -d` — fresh DB, all healthy
- [ ] `./mvnw spring-boot:run` — starts without errors on first run
- [ ] `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] `POST /api/auth/register` with a new phone number → 201
- [ ] `flyway:info` shows V1 applied (after M1)

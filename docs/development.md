# Development guide

How to run zalord locally, in recipes.

---

## 1. Prerequisites

| OS | Required | How |
|---|---|---|
| macOS | Docker Desktop, `make` (preinstalled) | `brew install --cask docker` |
| Linux | Docker Engine + Compose plugin, `make` | distro package manager |
| Windows | Docker Desktop, **`make`** | `choco install make` (cmd.exe) **or** install Git for Windows and use Git Bash (recommended — also gives you bash for the smoke test) |

Optional host tools (only needed for specific recipes below):

- `bash` — required for `make smoke`. On Windows: Git Bash or WSL.
- `wscat` — WebSocket client. `npm i -g wscat`.
- `jq` — pretty-prints JSON in shell pipelines. `brew install jq` / `apt install jq` / `choco install jq`.

You do **not** need host-installed `psql`, `redis-cli`, `cqlsh`, `kafka-topics.sh`, or `mc`. Every Make target shells into the relevant container.

---

## 2. First-time setup

```bash
git clone <repo>
cd zalord
cp .env.example .env       # then edit secrets (POSTGRES_PASSWORD, JWT_SECRET, ...)
make dev                   # pulls images, starts all containers
make dev-status            # wait until every service shows (healthy)
```

ScyllaDB takes 60–90s to become healthy on a cold start — don't panic on the first run.

---

## 3. Use cases

### 3.1 "I just want to bring everything up"

```bash
make dev                   # start
make dev-status            # check
make dev-logs              # tail every service
make dev-down              # stop, keep data
```

### 3.2 "Something's broken; reset to a clean slate"

```bash
make dev-reset             # docker compose down -v + up -d  (wipes ALL volumes)
```

Use this when DB schemas drift, when Kafka topics get into a bad state, or when you just want a known-good starting point. **It deletes every container's data**, so don't run it if you have demo data you want to keep.

### 3.3 "I'm working on auth-service and need fast iteration"

```bash
# After editing source code in backend/auth-service:
make rebuild SERVICE=auth-service       # rebuild image + restart that one container
make logs SERVICE=auth-service          # tail its logs in another terminal
```

`rebuild` uses `--no-deps`, so it only restarts auth-service — Postgres, Kafka, etc. stay up.

### 3.4 "Why isn't service X working?"

```bash
make logs SERVICE=<name>                # tail logs
make sh SERVICE=<name>                  # open a shell in the running container
make restart SERVICE=<name>             # restart without rebuilding (e.g. transient errors)
```

Inside `make sh` you can run `env`, `curl localhost:8080/health`, `ps`, etc. to inspect runtime state.

### 3.5 "I need to inspect the database"

```bash
make psql                               # default: zalord_auth
make psql DB=zalord_user                # any other service DB
make psql DB=zalord_chat
```

Once inside psql: `\dt` lists tables, `\d <table>` shows schema.

For ScyllaDB:

```bash
make cqlsh
# then inside:  USE zalord_messages;  DESCRIBE TABLES;
```

For Redis:

```bash
make redis-cli
# common queries:
#   KEYS refresh:*                  # active refresh tokens
#   HGETALL user:sessions:<uuid>    # session registry for a user
#   GET conversation:seq:<uuid>     # last sequence ID for a conversation
```

### 3.6 "Did the Kafka topic get created?"

```bash
make kafka-topics                       # list topics
make sh SERVICE=kafka                   # full kafka CLI access
# inside:  kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic chat.messages --from-beginning
```

### 3.7 "I need to look at uploaded media in MinIO"

Open the MinIO console in a browser:

```
http://localhost:9001
```

Login with `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` from `.env`. Bucket: `zalord-media`.

For CLI access:

```bash
make minio-sh
# inside:
#   mc alias set local http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD
#   mc ls local/zalord-media
```

### 3.8 "I want to run the end-to-end smoke test"

```bash
make smoke
```

Runs `scripts/sprint1-smoke.sh` — register a user, login, refresh, hit every `/health`, open a WebSocket, request a presigned URL, logout. Exits `0` only if everything passes.

**Windows note:** this target requires `bash`. Run from Git Bash or WSL, **not** plain cmd.exe.

### 3.9 "I'm working on the frontend"

```bash
make frontend-install                   # one-time: npm ci
make frontend                           # vite dev server on :5173
```

The frontend talks to the gateway at `http://localhost`. Make sure `make dev` is running first.

### 3.10 "I want to throw everything away"

```bash
make clean                              # down -v + remove locally built images
make nuke                               # clean + prune dangling docker volumes/networks (system-wide)
```

`nuke` touches every dangling docker resource on your machine, not just zalord's. Use sparingly.

### 3.11 "Production-style run on my VPS"

```bash
make prod-up
make prod-logs
make prod-down
```

Uses `docker-compose.prod.yml` (no port exposure, tighter resource limits, etc.). Not for local dev.

---

## 4. Windows-specific notes

The Makefile is written to run in **cmd.exe** and **PowerShell** with GnuWin32 / chocolatey `make`. Recipe lines avoid POSIX-only constructs (`[ ... ]` tests, `sh -c`, single-quoted strings).

That said: **Git Bash or WSL is strictly easier**, because:

- `scripts/sprint1-smoke.sh` (and any future bash helpers) just work — no `bash scripts/foo.sh` workaround.
- ANSI colour codes from docker logs render correctly.
- Forward-slash paths everywhere — no mental switching.

If you choose cmd.exe:

| Limitation | Workaround |
|---|---|
| `make smoke` needs bash | The target invokes `bash scripts/sprint1-smoke.sh` — `bash` must be on `PATH`. Install Git for Windows, then `bash` is available even from cmd. |
| `make help` output looks plain | Cosmetic. Functional. |
| Long lines wrap weirdly | Resize the console wider, or use Windows Terminal. |
| Docker volume paths | Use forward slashes in compose files — Docker normalises them. |

---

## 5. Common gotchas

- **`make build` fails with "no configuration file provided"** — there's no `docker-compose.yml` yet (it lands in Sprint 1 T2). `make help` works; `make build` will work once compose is in place.
- **ScyllaDB unhealthy for 60–90s** — it's just slow to boot. `depends_on: condition: service_healthy` is set on `message-service`, so it'll wait.
- **`make dev` fails on port conflict** — something on your host is already on 5432 / 6379 / 9092 / 9042 / 80. Either stop it or change the host-side port mapping in `docker-compose.yml`.
- **Auth fails with `INVALID_TOKEN` immediately after `make dev-reset`** — JWTs from before the reset still reference user IDs that no longer exist. Register a new user.
- **`make psql` errors with "role does not exist"** — your `.env` `POSTGRES_USER` differs from the default. Pass it explicitly: `make psql POSTGRES_USER=myuser`.

---

## 6. Reference

- Service responsibilities and APIs: [`services.md`](./services.md)
- Database schemas: [`database.md`](./database.md)
- Architecture diagrams: [`architecture.md`](./architecture.md)
- Core patterns (Outbox, Redis sequence, Presigned URL): [`patterns.md`](./patterns.md)
- Deployment topology + RAM budget: [`infrastructure.md`](./infrastructure.md)

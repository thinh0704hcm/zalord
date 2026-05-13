# ─────────────────────────────────────────────────────────────────────────────
# zalord Makefile
#
# Cross-platform requirements:
#   - macOS / Linux: just `make <target>` (uses the default /bin/sh).
#   - Windows:       run inside Git Bash, WSL, or MSYS2. From plain cmd.exe or
#                    PowerShell, `make` is not portable — `docker compose` and
#                    `sh` are not on PATH the same way. Install Git for Windows
#                    (includes Git Bash + make) and run from there.
#
# Every target ultimately shells out to `docker compose` or `docker`, which
# behave identically on Docker Desktop (Win/Mac) and Linux. We never rely on
# host-side tooling like psql / redis-cli / cqlsh / wscat — we `exec` into the
# container instead.
# ─────────────────────────────────────────────────────────────────────────────

SHELL := /bin/sh

# Parameters (override on the command line: `make logs SERVICE=auth-service`)
SERVICE ?=
DB      ?= zalord_auth

# Compose file selection (override with `make COMPOSE_FILES="-f a.yml -f b.yml" dev`)
COMPOSE_FILES ?=
DC = docker compose $(COMPOSE_FILES)

.PHONY: help \
        dev dev-down dev-reset dev-logs dev-status \
        build rebuild logs sh ps stop start restart \
        psql redis-cli cqlsh kafka-topics minio-mc \
        frontend frontend-install \
        ws-test smoke clean nuke \
        prod-up prod-down prod-logs

# Default target
help:
	@echo "zalord — make targets"
	@echo ""
	@echo "Local dev:"
	@echo "  make dev                    Start the full stack in the background"
	@echo "  make dev-down               Stop the stack (keep volumes)"
	@echo "  make dev-reset              Stop, wipe volumes, restart from scratch"
	@echo "  make dev-logs               Tail logs from every service"
	@echo "  make dev-status             docker compose ps"
	@echo ""
	@echo "Per-service:"
	@echo "  make build                  Build every service image"
	@echo "  make rebuild SERVICE=X      Rebuild and restart one service only"
	@echo "  make logs SERVICE=X         Tail one service's logs"
	@echo "  make sh SERVICE=X           Open an sh shell inside a running container"
	@echo "  make restart SERVICE=X      Restart one service without rebuilding"
	@echo ""
	@echo "Infra shells (no host tooling required):"
	@echo "  make psql DB=zalord_auth    psql into a Postgres database"
	@echo "  make redis-cli              redis-cli inside the redis container"
	@echo "  make cqlsh                  cqlsh inside the scylladb container"
	@echo "  make kafka-topics           List Kafka topics"
	@echo "  make minio-mc               mc shell against the local MinIO"
	@echo ""
	@echo "Frontend:"
	@echo "  make frontend               Vite dev server"
	@echo "  make frontend-install       npm ci"
	@echo ""
	@echo "Testing:"
	@echo "  make smoke                  Run scripts/sprint1-smoke.sh end-to-end"
	@echo "  make ws-test                Print wscat usage for the chat gateway"
	@echo ""
	@echo "Cleanup:"
	@echo "  make clean                  down -v + remove built images"
	@echo "  make nuke                   clean + prune dangling volumes/networks"
	@echo ""
	@echo "Prod:"
	@echo "  make prod-up | prod-down | prod-logs"

# ── Local dev ────────────────────────────────────────────────────────────────
dev:
	$(DC) up -d

dev-down:
	$(DC) down

dev-reset:
	$(DC) down -v
	$(DC) up -d

dev-logs:
	$(DC) logs -f

dev-status ps:
	$(DC) ps

stop:
	$(DC) stop

start:
	$(DC) start

# ── Build / per-service ──────────────────────────────────────────────────────
build:
	$(DC) build

rebuild:
	@if [ -z "$(SERVICE)" ]; then echo "ERROR: SERVICE=<name> required"; exit 1; fi
	$(DC) build $(SERVICE)
	$(DC) up -d --no-deps $(SERVICE)

logs:
	@if [ -z "$(SERVICE)" ]; then echo "ERROR: SERVICE=<name> required"; exit 1; fi
	$(DC) logs -f $(SERVICE)

sh:
	@if [ -z "$(SERVICE)" ]; then echo "ERROR: SERVICE=<name> required"; exit 1; fi
	$(DC) exec $(SERVICE) sh

restart:
	@if [ -z "$(SERVICE)" ]; then echo "ERROR: SERVICE=<name> required"; exit 1; fi
	$(DC) restart $(SERVICE)

# ── Infra shells ─────────────────────────────────────────────────────────────
# Use `sh -c` + escaped `$$VAR` so the container expands its own env (works
# identically on every host OS — no host shell quoting nightmare).

psql:
	$(DC) exec postgres sh -c 'psql -U "$$POSTGRES_USER" -d $(DB)'

redis-cli:
	$(DC) exec redis redis-cli

cqlsh:
	$(DC) exec scylladb cqlsh

kafka-topics:
	$(DC) exec kafka sh -c 'kafka-topics.sh --bootstrap-server localhost:9092 --list'

minio-mc:
	$(DC) exec minio sh -c 'mc alias set local http://localhost:9000 "$$MINIO_ROOT_USER" "$$MINIO_ROOT_PASSWORD" >/dev/null && sh'

# ── Frontend ─────────────────────────────────────────────────────────────────
frontend:
	cd frontend && npm run dev

frontend-install:
	cd frontend && npm ci

# ── Testing ──────────────────────────────────────────────────────────────────
smoke:
	@test -x scripts/sprint1-smoke.sh || (echo "scripts/sprint1-smoke.sh missing or not executable"; exit 1)
	./scripts/sprint1-smoke.sh

ws-test:
	@echo "Usage:"
	@echo "  npm i -g wscat                                  # one-time"
	@echo "  wscat -c \"ws://localhost/ws?token=<jwt>\"      # chat gateway"
	@echo "  wscat -c \"ws://localhost/ws/push?token=<jwt>\" # push gateway"

# ── Cleanup ──────────────────────────────────────────────────────────────────
clean:
	$(DC) down -v --rmi local

nuke: clean
	docker volume prune -f
	docker network prune -f

# ── Prod ─────────────────────────────────────────────────────────────────────
prod-up:
	docker compose -f docker-compose.prod.yml up -d

prod-down:
	docker compose -f docker-compose.prod.yml down

prod-logs:
	docker compose -f docker-compose.prod.yml logs -f

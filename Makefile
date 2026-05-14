# ─────────────────────────────────────────────────────────────────────────────
# zalord Makefile — Week 1 scope
#
# Active targets cover only what Week 1 needs: bring the stack up, manage the
# two service containers (auth-service, user-service), peek into Postgres /
# Redis, run the smoke test. Week 2+ targets are parked at the bottom.
#
# Works in:
#   - macOS / Linux                native sh
#   - Windows Git Bash / WSL       sh
#   - Windows cmd.exe / PowerShell with make (e.g. choco install make)
#
# See docs/development.md for use-case recipes.
# ─────────────────────────────────────────────────────────────────────────────

# Load .env into make if present, so $(POSTGRES_USER) etc. are usable.
ifneq (,$(wildcard .env))
include .env
export
endif

# Defaults — match .env.example.
POSTGRES_USER ?= pguser
SERVICE       ?=
# `make psql` defaults to auth-pg / auth-db. Override with `make psql PG=user`.
PG            ?= auth
DB            ?= $(PG)-db

# Compose. Override with: make COMPOSE_FILES="-f a.yml -f b.yml" dev
COMPOSE_FILES ?=
DC := docker compose
ifneq ($(strip $(COMPOSE_FILES)),)
  DC := $(DC) $(COMPOSE_FILES)
endif

.PHONY: help \
        dev dev-down dev-reset dev-logs dev-status ps stop start \
        build rebuild logs sh restart \
        psql redis-cli \
        smoke \
        clean nuke \
        require-service

# ── Help ─────────────────────────────────────────────────────────────────────
# Example: make help
# Print every target with a one-line description.
help:
	@echo zalord -- Week 1 make targets
	@echo Local dev:
	@echo "  make dev                    Start the full stack in the background"
	@echo "  make dev-down               Stop the stack [keeps volumes]"
	@echo "  make dev-reset              Stop, wipe volumes, restart from scratch"
	@echo "  make dev-logs               Tail logs from every service"
	@echo "  make dev-status             docker compose ps"
	@echo Per-service:
	@echo "  make build                  Build every service image"
	@echo "  make rebuild SERVICE=X      Rebuild and restart one service"
	@echo "  make logs SERVICE=X         Tail one service logs"
	@echo "  make sh SERVICE=X           Open sh inside a running container"
	@echo "  make restart SERVICE=X      Restart one service without rebuilding"
	@echo Infra shells:
	@echo "  make psql                   psql into auth-pg / auth-db"
	@echo "  make psql PG=user           psql into user-pg / user-db"
	@echo "  make redis-cli              redis-cli inside the redis container"
	@echo Testing:
	@echo "  make smoke                  Run scripts/sprint1-smoke.sh [bash only]"
	@echo Cleanup:
	@echo "  make clean                  down -v + remove built images"
	@echo "  make nuke                   clean + prune dangling volumes/networks"
	@echo See docs/development.md for use-case recipes.

# ── Service-argument guard (pure-make, works on cmd + sh) ────────────────────
# Internal helper. Targets that need SERVICE=<name> depend on this and fail
# fast with a readable error if the user forgot to pass SERVICE.
require-service:
ifndef SERVICE
	$(error SERVICE=<name> required, e.g. make logs SERVICE=auth-service)
endif

# ── Local dev ────────────────────────────────────────────────────────────────

# Example: make dev
# Boot the whole compose stack in detached mode. Idempotent — re-running is safe.
dev:
	$(DC) up -d

# Example: make dev-down
# Stop and remove every container. Volumes are preserved, so data survives.
dev-down:
	$(DC) down

# Example: make dev-reset
# Nuclear reset: stop, wipe ALL volumes, start fresh. Use when DB / Kafka /
# Redis state is corrupted or you want a guaranteed-clean baseline.
dev-reset:
	$(DC) down -v
	$(DC) up -d

# Example: make dev-logs
# Stream merged logs from every container. Ctrl-C to detach.
dev-logs:
	$(DC) logs -f

# Example: make dev-status     OR     make ps
# Snapshot of every container with health + uptime. The go-to "is anything red?".
dev-status ps:
	$(DC) ps

# Example: make stop
# Pause every container but keep them defined. Faster than down/up if you'll
# resume shortly. Followed by `make start`.
stop:
	$(DC) stop

# Example: make start
# Resume containers previously stopped with `make stop`.
start:
	$(DC) start

# ── Build / per-service ──────────────────────────────────────────────────────

# Example: make build
# Build images for every service with a `build:` directive in compose.
# Currently a no-op until you uncomment auth-service / user-service.
build:
	$(DC) build

# Example: make rebuild SERVICE=auth-service
# Rebuild ONE service's image and restart only that container. Uses --no-deps
# so dependencies (postgres, redis) stay up. The fast-iteration target while
# coding on a single service.
rebuild: require-service
	$(DC) build $(SERVICE)
	$(DC) up -d --no-deps $(SERVICE)

# Example: make logs SERVICE=auth-service
# Stream logs from one container instead of the firehose `dev-logs` gives you.
logs: require-service
	$(DC) logs -f $(SERVICE)

# Example: make sh SERVICE=auth-pg
# Open an interactive sh inside a running container. Use to inspect env, run
# ad-hoc commands, poke at the filesystem. Container must be running.
sh: require-service
	$(DC) exec $(SERVICE) sh

# Example: make restart SERVICE=nginx
# Restart one container without rebuilding the image. Use after editing a
# mounted config file (e.g. nginx.conf) that the container reads at startup.
restart: require-service
	$(DC) restart $(SERVICE)

# ── Infra shells ─────────────────────────────────────────────────────────────
# Container naming convention: <svc>-pg owns DB <svc>-db.

# Example: make psql            OR     make psql PG=user            OR     make psql PG=auth DB=postgres
# Drop into psql against a per-service Postgres container. With no args you
# land in auth-pg's auth-db. PG=user switches to user-pg / user-db. DB=...
# lets you connect to a different database inside the same container (e.g.
# the bootstrap `postgres` DB).
psql:
	$(DC) exec $(PG)-pg psql -U $(POSTGRES_USER) -d $(DB)

# Example: make redis-cli
# Drop into redis-cli inside the shared redis container. From here you can
# `KEYS refresh:*`, `HGETALL user:sessions:<uuid>`, etc.
redis-cli:
	$(DC) exec redis redis-cli

# ── Testing ──────────────────────────────────────────────────────────────────

# Example: make smoke
# Run the end-to-end smoke test against a running stack. Exits 0 only if
# every step passes. Requires bash on PATH (Git Bash / WSL on Windows).
smoke:
	@echo Running scripts/sprint1-smoke.sh (requires bash)
	bash scripts/sprint1-smoke.sh

# ── Cleanup ──────────────────────────────────────────────────────────────────

# Example: make clean
# Tear the stack down, wipe its volumes, AND remove images this project built
# locally. Pulled base images (postgres:16-alpine etc.) stay cached.
clean:
	$(DC) down -v --rmi local

# Example: make nuke
# `make clean` plus a system-wide docker volume + network prune. Touches
# every dangling resource on your machine, not just zalord's. Use sparingly.
nuke: clean
	docker volume prune -f
	docker network prune -f


# ─────────────────────────────────────────────────────────────────────────────
# WEEK 2+ TARGETS — commented out until the matching infra comes online.
#
# Uncomment a block when you uncomment the corresponding service / container
# in docker-compose.yml. Add the target name to the .PHONY list above too.
# ─────────────────────────────────────────────────────────────────────────────

# ── ScyllaDB shell (uncomment when scylladb container is enabled) ──
# Example: make cqlsh
# Drop into cqlsh inside the scylladb container to inspect message storage.
# cqlsh:
# 	$(DC) exec scylladb cqlsh

# ── Kafka topics (uncomment when kafka container is enabled) ──
# Example: make kafka-topics
# List every Kafka topic — quick check that chat.messages / media.uploaded
# exist after kafka-init has run.
# kafka-topics:
# 	$(DC) exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# ── MinIO shell (uncomment when minio container is enabled) ──
# Example: make minio-sh
# Drop into sh inside the minio container. Run `mc alias set local ...`
# manually to use the mc CLI against the running MinIO.
# MINIO_ROOT_USER     ?= minioadmin
# MINIO_ROOT_PASSWORD ?= minioadmin
# minio-sh:
# 	$(DC) exec minio sh

# ── Frontend (uncomment when frontend work begins) ──
# Example: make frontend            OR     make frontend-install
# Start the Vite dev server (frontend) or install npm deps (frontend-install).
# frontend:
# 	cd frontend && npm run dev
#
# frontend-install:
# 	cd frontend && npm ci

# ── WebSocket test helper (uncomment when chat-service is added in Week 2) ──
# Example: make ws-test
# Print wscat invocation examples for connecting to the chat / push gateways.
# Not a real test — just a copy-paste helper.
# ws-test:
# 	@echo Install wscat:  npm i -g wscat
# 	@echo Connect chat:   wscat -c "ws://localhost/ws?token=<jwt>"
# 	@echo Connect push:   wscat -c "ws://localhost/ws/push?token=<jwt>"

# ── Production stack (uncomment when docker-compose.prod.yml exists) ──
# Example: make prod-up        OR    make prod-down        OR    make prod-logs
# Manage the production compose stack (tighter resource limits, no debug
# ports exposed). Local-dev flow stays on the default docker-compose.yml.
# prod-up:
# 	docker compose -f docker-compose.prod.yml up -d
#
# prod-down:
# 	docker compose -f docker-compose.prod.yml down
#
# prod-logs:
# 	docker compose -f docker-compose.prod.yml logs -f

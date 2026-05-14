# Load .env into make if present, so $(POSTGRES_USER) etc. are usable in
# recipes regardless of the host shell.
ifneq (,$(wildcard .env))
include .env
export
endif

# Defaults — match .env.example. Overridden by .env values above when present.
POSTGRES_USER       ?= zalord
MINIO_ROOT_USER     ?= minioadmin
MINIO_ROOT_PASSWORD ?= minioadmin
SERVICE             ?=
# `make psql SERVICE=auth` connects to postgres-auth, database zalord_auth.
# Override DB to connect to a non-default database inside that container.
PG                  ?= auth
DB                  ?= zalord_$(PG)

# Compose. Override with: make COMPOSE_FILES="-f a.yml -f b.yml" dev
COMPOSE_FILES ?=
DC := docker compose
ifneq ($(strip $(COMPOSE_FILES)),)
  DC := $(DC) $(COMPOSE_FILES)
endif

.PHONY: help \
        dev dev-down dev-reset dev-logs dev-status ps stop start \
        build rebuild logs sh restart \
        psql redis-cli cqlsh kafka-topics minio-sh \
        frontend frontend-install \
        ws-test smoke clean nuke \
        prod-up prod-down prod-logs \
        require-service

help:
	@echo zalord -- make targets
	@echo Local dev:
	@echo   make dev                    Start the full stack in the background
	@echo   make dev-down               Stop the stack (keep volumes)
	@echo   make dev-reset              Stop, wipe volumes, restart from scratch
	@echo   make dev-logs               Tail logs from every service
	@echo   make dev-status             docker compose ps
	@echo Per-service:
	@echo   make build                  Build every service image
	@echo   make rebuild SERVICE=X      Rebuild and restart one service
	@echo   make logs SERVICE=X         Tail one service's logs
	@echo   make sh SERVICE=X           Open sh inside a running container
	@echo   make restart SERVICE=X      Restart one service without rebuilding
	@echo Infra shells:
	@echo   make psql                   psql into postgres-auth (default)
	@echo   make psql PG=user           psql into postgres-user
	@echo   make psql PG=auth DB=other  psql into a non-default DB in postgres-auth
	@echo   make redis-cli              redis-cli inside the redis container
	@echo   make cqlsh                  cqlsh inside the scylladb container
	@echo   make kafka-topics           List Kafka topics
	@echo   make minio-sh               sh inside the minio container
	@echo Frontend:
	@echo   make frontend               Vite dev server
	@echo   make frontend-install       npm ci
	@echo Testing:
	@echo   make smoke                  Run scripts/sprint1-smoke.sh (bash only)
	@echo   make ws-test                Print wscat usage for the chat gateway
	@echo Cleanup:
	@echo   make clean                  down -v + remove built images
	@echo   make nuke                   clean + prune dangling volumes/networks
	@echo Prod:
	@echo   make prod-up ^| prod-down ^| prod-logs
	@echo See docs/development.md for use-case recipes.

# ── Service-argument guard (pure-make, works on cmd + sh) ────────────────────
require-service:
ifndef SERVICE
	$(error SERVICE=<name> required, e.g. make logs SERVICE=auth-service)
endif

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

rebuild: require-service
	$(DC) build $(SERVICE)
	$(DC) up -d --no-deps $(SERVICE)

logs: require-service
	$(DC) logs -f $(SERVICE)

sh: require-service
	$(DC) exec $(SERVICE) sh

restart: require-service
	$(DC) restart $(SERVICE)

# ── Infra shells ─────────────────────────────────────────────────────────────
# Vars expanded by make (not by the host shell), so cmd.exe is happy.
psql:
	$(DC) exec postgres-$(PG) psql -U $(POSTGRES_USER) -d $(DB)

redis-cli:
	$(DC) exec redis redis-cli

cqlsh:
	$(DC) exec scylladb cqlsh

kafka-topics:
	$(DC) exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

minio-sh:
	$(DC) exec minio sh

# ── Frontend ─────────────────────────────────────────────────────────────────
frontend:
	cd frontend && npm run dev

frontend-install:
	cd frontend && npm ci

# ── Testing ──────────────────────────────────────────────────────────────────
smoke:
	@echo Running scripts/sprint1-smoke.sh (requires bash)
	bash scripts/sprint1-smoke.sh

ws-test:
	@echo Install wscat:  npm i -g wscat
	@echo Connect chat:   wscat -c "ws://localhost/ws?token=<jwt>"
	@echo Connect push:   wscat -c "ws://localhost/ws/push?token=<jwt>"

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

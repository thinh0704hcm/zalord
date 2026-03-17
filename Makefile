.PHONY: dev dev-down dev-logs dev-reset test test-coverage backend frontend install prod-up prod-down prod-logs

# ── Local dev ─────────────────────────────────────────────────────────────────
dev:
	docker compose up -d

dev-down:
	docker compose down

dev-logs:
	docker compose logs -f

dev-reset:
	docker compose down -v
	docker compose up -d

# ── Backend ───────────────────────────────────────────────────────────────────
backend:
	@cd backend && export $(shell grep -v '^#' .env | xargs) && ./mvnw spring-boot:run

test:
	@cd backend && export $(shell grep -v '^#' .env | xargs) && ./mvnw test

test-coverage:
	@cd backend && export $(shell grep -v '^#' .env | xargs) && ./mvnw test jacoco:report

# ── Frontend ──────────────────────────────────────────────────────────────────
frontend:
	cd frontend && npm run dev

install:
	cd frontend && npm ci

# ── Prod ──────────────────────────────────────────────────────────────────────
prod-up:
	docker compose -f docker-compose.prod.yml up -d

prod-down:
	docker compose -f docker-compose.prod.yml down

prod-logs:
	docker compose -f docker-compose.prod.yml logs -f
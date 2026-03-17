.PHONY: dev dev-down dev-logs test build

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
test:
	cd zalord-backend && ./mvnw test

test-coverage:
	cd zalord-backend && ./mvnw test jacoco:report

backend:
	cd zalord-backend && ./mvnw spring-boot:run

# ── Frontend ──────────────────────────────────────────────────────────────────
frontend:
	cd zalord-frontend && npm run dev

install:
	cd zalord-frontend && npm ci

# ── Prod ──────────────────────────────────────────────────────────────────────
prod-up:
	docker compose -f docker-compose.prod.yml up -d

prod-down:
	docker compose -f docker-compose.prod.yml down

prod-logs:
	docker compose -f docker-compose.prod.yml logs -f
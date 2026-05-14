-- Shared init script for every per-service Postgres container.
--
-- Each Postgres container creates its own database via POSTGRES_DB env var,
-- then runs this file against that database on first boot. Since every service
-- needs gen_random_uuid() in V1 migrations, we enable pgcrypto here once and
-- it's available everywhere.
--
-- Tables are still owned by each service and created by its own migrations
-- (Flyway / golang-migrate). infra/ never declares service tables.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

# M6 — Actuator Metrics Baseline

Captured: 2026-04-13  
Environment: local Docker (PostgreSQL 16.13, Redis 7.4.8)  
App: Spring Boot 4.0.3 / Java 21.0.10

## Startup

| Metric | Value |
|---|---|
| Application started time | 31.5 s |
| JVM memory used at startup | 242 MB |

## HTTP Latency (cold, first request)

| Endpoint | Count | Avg | Max |
|---|---|---|---|
| POST /api/auth/register | 1 | 2873 ms | 2873 ms |
| POST /api/auth/login | 1 | ~50 ms | ~50 ms |

> Note: register p50 includes first-time DB connection pool warmup. Subsequent requests will be significantly faster. This is the Stage 1 cold baseline for comparison with Stage 2.

## Infrastructure Health

| Component | Status |
|---|---|
| PostgreSQL | UP |
| Redis | UP |
| Disk space | UP (28 GB free) |
| Overall | UP |

## Available Metrics
Actuator exposes: http.server.requests, hikaricp.connections, jvm.memory.*, jvm.gc.*, process.cpu.usage, spring.security.filterchains.*, logback.events

## Stage 2 Comparison Notes
- Startup time target: each microservice should start in <10s (no Hibernate schema validation overhead)
- Register latency target: p99 <200ms after warmup (connection pool pre-warmed)
- Memory: per-service footprint expected ~100-150 MB vs 242 MB monolith

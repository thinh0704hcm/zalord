# Zalord — Distributed Chat System

A production-grade chat system built with microservices architecture, designed to handle 10,000 concurrent users. Inspired by systems like Zalo and Messenger, with a focus on real-time messaging, reliability, and scalability.

## Project Context

| Item | Detail |
|---|---|
| Type | Microservices Development Course Project |
| Team size | 3 people |
| Duration | ~3 months |
| Scale target | 10,000 concurrent users |
| Deployment | Single VPS (8GB RAM) via Docker Compose |

## Core Features

| Feature | Category |
|---|---|
| 1-on-1 messaging | Core |
| Group chat | Core |
| Real-time delivery (WebSocket) | Core |
| Message history | Core |
| User registration & login | Core |
| Online presence / last seen | Supporting |
| Read receipts | Supporting |
| Typing indicators | Supporting |
| Friend / contact management | Supporting |
| File & media sharing | Supporting |
| Push notifications (FCM) | Supporting |
| Message search | Supporting |

## Key Architecture Decisions

- **Kafka** as the central message broker — enables the Transactional Outbox pattern for guaranteed delivery
- **ScyllaDB** for message storage — high write throughput, optimized for time-series chat data
- **PostgreSQL** for relational data — users, groups, friendships, metadata
- **Redis** for caching, session management, presence, and sequence generation
- **MinIO** for self-hosted object storage — media/file uploads via Presigned URL
- **Docker Compose** for deployment — right-sized for a single VPS; K8s manifests provided as a bonus

## Documents

| File | Description |
|---|---|
| [architecture.md](./architecture.md) | Service map, tech stack, communication patterns |
| [services.md](./services.md) | Per-service responsibility and API surface |
| [database.md](./database.md) | PostgreSQL, ScyllaDB schemas and Redis key design |
| [patterns.md](./patterns.md) | Design patterns: Outbox, Redis Sequence, Presigned URL |
| [infrastructure.md](./infrastructure.md) | Deployment, resource allocation, Docker Compose layout |
| [roadmap.md](./roadmap.md) | 3-month sprint plan and team responsibilities |

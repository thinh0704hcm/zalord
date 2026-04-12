# Infrastructure

## Deployment Overview

All services run on a **single VPS (8GB RAM)** using **Docker Compose**. This keeps operational complexity low for a 3-month project while still demonstrating a genuine microservices deployment.

> Kubernetes (K3s) manifests can be written as a bonus deliverable once the core system is stable, but running K3s + all services on 8GB is unnecessarily risky during development.

---

## RAM Budget

| Component | Estimated RAM | Notes |
|---|---|---|
| Auth Service (Java) | 512MB | Spring Boot baseline ~300MB |
| Group Service (Java) | 512MB | |
| Message Service (Java) | 768MB | Kafka consumer + ScyllaDB driver |
| Media Service (Java) | 512MB | |
| Chat Service (Go) | 256MB | WebSocket connections are cheap in Go |
| User Service (Go) | 128MB | |
| Push Service (Go) | 256MB | |
| Message Relay (Go) | 128MB | |
| Notification Service (Go) | 128MB | |
| Nginx | 64MB | |
| Kafka (1 broker) | 1024MB | Set `KAFKA_HEAP_OPTS=-Xmx1g -Xms512m` |
| Zookeeper | 256MB | Required by Kafka |
| PostgreSQL | 512MB | `shared_buffers=128MB` |
| ScyllaDB (1 node) | 1024MB | `--memory 1G` flag |
| Redis (AOF) | 256MB | |
| MinIO | 256MB | |
| **Total** | **~6.6GB** | ~1.4GB headroom for OS + spikes |

---

## Docker Compose Layout

```
zalord/
├── docker-compose.yml
├── docker-compose.override.yml     # local dev overrides (hot reload, debug ports)
├── .env                            # secrets and config (never commit)
├── nginx/
│   └── nginx.conf
├── services/
│   ├── auth-service/
│   ├── user-service/
│   ├── group-service/
│   ├── chat-service/
│   ├── message-relay/
│   ├── push-service/
│   ├── message-service/
│   ├── notification-service/
│   └── media-service/
└── infra/
    ├── postgres/
    │   └── init.sql
    ├── scylladb/
    │   └── init.cql
    ├── kafka/
    └── redis/
        └── redis.conf
```

---

## docker-compose.yml (skeleton)

```yaml
version: "3.9"

networks:
  zalord-net:
    driver: bridge

volumes:
  postgres_data:
  scylla_data:
  redis_data:
  minio_data:
  kafka_data:

services:

  # ── Infrastructure ──────────────────────────────────────────────

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: zalord
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./infra/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql
    deploy:
      resources:
        limits:
          memory: 512M
    networks: [zalord-net]

  scylladb:
    image: scylladb/scylla:5.4
    command: --memory 1G --smp 1
    volumes:
      - scylla_data:/var/lib/scylla
    networks: [zalord-net]

  redis:
    image: redis:7-alpine
    command: redis-server /usr/local/etc/redis/redis.conf
    volumes:
      - redis_data:/data
      - ./infra/redis/redis.conf:/usr/local/etc/redis/redis.conf
    deploy:
      resources:
        limits:
          memory: 256M
    networks: [zalord-net]

  zookeeper:
    image: bitnami/zookeeper:latest
    environment:
      ALLOW_ANONYMOUS_LOGIN: "yes"
    deploy:
      resources:
        limits:
          memory: 256M
    networks: [zalord-net]

  kafka:
    image: bitnami/kafka:latest
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_HEAP_OPTS: "-Xmx1g -Xms512m"
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    depends_on: [zookeeper]
    volumes:
      - kafka_data:/bitnami/kafka
    deploy:
      resources:
        limits:
          memory: 1G
    networks: [zalord-net]

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD}
    volumes:
      - minio_data:/data
    ports:
      - "9000:9000"
      - "9001:9001"   # MinIO console (dev only, remove in prod)
    deploy:
      resources:
        limits:
          memory: 256M
    networks: [zalord-net]

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro   # SSL certs
    depends_on:
      - auth-service
      - user-service
      - chat-service
      - push-service
    networks: [zalord-net]

  # ── Application Services ────────────────────────────────────────

  auth-service:
    build: ./services/auth-service
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/zalord
      DB_USER: ${POSTGRES_USER}
      DB_PASSWORD: ${POSTGRES_PASSWORD}
      REDIS_URL: redis://redis:6379
      JWT_SECRET: ${JWT_SECRET}
    deploy:
      resources:
        limits:
          memory: 512M
    depends_on: [postgres, redis]
    networks: [zalord-net]

  user-service:
    build: ./services/user-service
    environment:
      DB_DSN: postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/zalord
      REDIS_ADDR: redis:6379
    deploy:
      resources:
        limits:
          memory: 128M
    depends_on: [postgres, redis]
    networks: [zalord-net]

  group-service:
    build: ./services/group-service
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/zalord
      DB_USER: ${POSTGRES_USER}
      DB_PASSWORD: ${POSTGRES_PASSWORD}
    deploy:
      resources:
        limits:
          memory: 512M
    depends_on: [postgres]
    networks: [zalord-net]

  chat-service:
    build: ./services/chat-service
    environment:
      DB_DSN: postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/zalord
      REDIS_ADDR: redis:6379
      USER_SERVICE_ADDR: user-service:50051
      GROUP_SERVICE_ADDR: group-service:50052
    deploy:
      resources:
        limits:
          memory: 256M
    depends_on: [postgres, redis, user-service, group-service]
    networks: [zalord-net]

  message-relay:
    build: ./services/message-relay
    environment:
      DB_DSN: postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/zalord
      KAFKA_BROKERS: kafka:9092
    deploy:
      resources:
        limits:
          memory: 128M
    depends_on: [postgres, kafka]
    networks: [zalord-net]

  push-service:
    build: ./services/push-service
    environment:
      KAFKA_BROKERS: kafka:9092
      REDIS_ADDR: redis:6379
      USER_SERVICE_ADDR: user-service:50051
    deploy:
      resources:
        limits:
          memory: 256M
    depends_on: [kafka, redis, user-service]
    networks: [zalord-net]

  message-service:
    build: ./services/message-service
    environment:
      KAFKA_BROKERS: kafka:9092
      SCYLLA_HOSTS: scylladb:9042
      DB_URL: jdbc:postgresql://postgres:5432/zalord
    deploy:
      resources:
        limits:
          memory: 768M
    depends_on: [kafka, scylladb]
    networks: [zalord-net]

  notification-service:
    build: ./services/notification-service
    environment:
      KAFKA_BROKERS: kafka:9092
      DB_DSN: postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/zalord
      FCM_KEY: ${FCM_SERVER_KEY}
      USER_SERVICE_ADDR: user-service:50051
    deploy:
      resources:
        limits:
          memory: 128M
    depends_on: [kafka, postgres]
    networks: [zalord-net]

  media-service:
    build: ./services/media-service
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/zalord
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_USER}
      MINIO_SECRET_KEY: ${MINIO_PASSWORD}
      MINIO_BUCKET: zalord-media
      KAFKA_BROKERS: kafka:9092
    deploy:
      resources:
        limits:
          memory: 512M
    depends_on: [postgres, minio]
    networks: [zalord-net]
```

---

## Nginx Routing

```nginx
upstream chat_ws    { server chat-service:8080; }
upstream push_ws    { server push-service:8081; }
upstream auth_api   { server auth-service:8082; }
upstream user_api   { server user-service:8083; }
upstream group_api  { server group-service:8084; }
upstream message_api{ server message-service:8085; }
upstream media_api  { server media-service:8086; }

server {
    listen 443 ssl;
    server_name yourdomain.com;

    # WebSocket — chat (send)
    location /ws/chat {
        proxy_pass http://chat_ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # WebSocket — push (receive)
    location /ws/push {
        proxy_pass http://push_ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    location /api/auth/    { proxy_pass http://auth_api; }
    location /api/users/   { proxy_pass http://user_api; }
    location /api/groups/  { proxy_pass http://group_api; }
    location /api/messages/{ proxy_pass http://message_api; }
    location /api/media/   { proxy_pass http://media_api; }
}
```

---

## Redis Configuration (redis.conf)

```conf
# Persistence — AOF mode
appendonly yes
appendfsync everysec

# Memory limit
maxmemory 256mb
maxmemory-policy allkeys-lru
```

---

## Environment Variables (.env)

```env
POSTGRES_USER=zalord
POSTGRES_PASSWORD=changeme
JWT_SECRET=supersecretkey
MINIO_USER=minioadmin
MINIO_PASSWORD=minioadmin
FCM_SERVER_KEY=your_fcm_key_here
```

> Never commit `.env` to version control. Add it to `.gitignore`.

---

## Kafka Topics

| Topic | Partitions | Retention | Consumers |
|---|---|---|---|
| `chat.messages` | 4 | 7 days | Message Service, Push Service, Notification Service |
| `media.uploaded` | 1 | 1 day | Message Service |

---

## Health Checks & Monitoring (Optional, Sprint 3)

- Each service exposes `GET /health` returning `200 OK`
- Nginx can use `proxy_next_upstream` for basic failover
- Docker Compose `healthcheck` blocks for infra services
- Consider **Prometheus + Grafana** (lightweight stack, ~300MB combined) for basic metrics if RAM allows

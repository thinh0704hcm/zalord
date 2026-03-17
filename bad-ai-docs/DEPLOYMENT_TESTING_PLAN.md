# giano — Deployment & Testing Plan
## Infrastructure, Load Testing, and Comparison Study

> This document covers everything outside the codebase: server setup, deployment topology,
> load test design, resilience experiments, and the comparison report methodology.
> Software implementation is in `DEVELOPMENT_PLAN.md`.

---

## Infrastructure Topology

### Server Assignments

```
┌──────────────────────────────────────────────────────────────────┐
│  VPS1 — Vietnam (strongest)                                      │
│                                                                  │
│  Stage 1: giano monolith app + PgBouncer                        │
│  Stage 2: Nginx gateway + msg-service (WS-intensive)            │
│                                                                  │
│  Exposed ports: 80 (Stage 2 only), 8080 (Stage 1)              │
└──────────────────────────┬───────────────────────────────────────┘
                           │ WireGuard tunnel (VN–VN, ~1–5ms)
┌──────────────────────────▼───────────────────────────────────────┐
│  VPS2 — Vietnam                                                  │
│                                                                  │
│  Stage 1: PostgreSQL + Redis + PgBouncer (infra only)           │
│  Stage 2: auth-service + room-service + presence-service        │
│           + auth_db + room_db + msg_db + Redis + RabbitMQ       │
│                                                                  │
│  Exposed ports: internal only (WireGuard)                       │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│  VPS3 — Singapore                                                │
│                                                                  │
│  Both stages: k6 + Prometheus + Grafana + cAdvisor              │
│  Scrapes VPS1 + VPS2 via public endpoints                       │
│                                                                  │
│  Exposed ports: 3000 (Grafana UI), 9090 (Prometheus)           │
└──────────────────────────────────────────────────────────────────┘
```

**Why VPS3 (SG) for load testing:**
k6 running from Singapore adds ~40ms consistent latency to every request — identical for both stages, so the comparison remains valid. This also simulates a real external user rather than localhost, making latency numbers more meaningful.

**Why VPS2 is internal-only:**
Database and RabbitMQ must never be directly accessible from the internet. All inter-service communication in Stage 2 goes through the WireGuard tunnel.

---

## Phase D0 — Base Infrastructure (Both Stages)

**Do this once, before any deployment.**

### WireGuard Setup (VPS1 ↔ VPS2)

```bash
# On both VPS1 and VPS2
apt install wireguard

# Generate keys
wg genkey | tee /etc/wireguard/private.key | wg pubkey > /etc/wireguard/public.key
chmod 600 /etc/wireguard/private.key

# VPS1 config: /etc/wireguard/wg0.conf
[Interface]
Address = 10.0.0.1/24
PrivateKey = <VPS1_PRIVATE_KEY>
ListenPort = 51820

[Peer]
PublicKey = <VPS2_PUBLIC_KEY>
AllowedIPs = 10.0.0.2/32
Endpoint = <VPS2_PUBLIC_IP>:51820
PersistentKeepalive = 25

# VPS2 config: /etc/wireguard/wg0.conf
[Interface]
Address = 10.0.0.2/24
PrivateKey = <VPS2_PRIVATE_KEY>
ListenPort = 51820

[Peer]
PublicKey = <VPS1_PUBLIC_KEY>
AllowedIPs = 10.0.0.1/32
Endpoint = <VPS1_PUBLIC_IP>:51820
PersistentKeepalive = 25

# Enable on both
systemctl enable wg-quick@wg0
systemctl start wg-quick@wg0

# Verify
ping 10.0.0.2  # from VPS1 → should reply
```

**This step is itself an operational complexity data point.** Record setup time. Monolith needs zero network configuration between servers.

### Firewall Rules

```bash
# VPS1 — allow inbound HTTP/WS from public + WireGuard
ufw allow 22/tcp           # SSH
ufw allow 80/tcp           # Nginx (Stage 2)
ufw allow 8080/tcp         # App direct (Stage 1)
ufw allow in on wg0        # All WireGuard traffic
ufw enable

# VPS2 — internal only
ufw allow 22/tcp
ufw allow in on wg0        # Only WireGuard
ufw deny from any to any   # Block all public inbound
ufw enable

# VPS3 — observability
ufw allow 22/tcp
ufw allow 3000/tcp         # Grafana
ufw allow 9090/tcp         # Prometheus (restrict to team IPs ideally)
ufw enable
```

### cAdvisor (All 3 VPS)

```yaml
# docker-compose.observability.yml — deploy on all 3 VPS
services:
  cadvisor:
    image: gcr.io/cadvisor/cadvisor:latest
    restart: unless-stopped
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - /sys:/sys:ro
      - /var/lib/docker/:/var/lib/docker:ro
    ports:
      - "8090:8080"
    command:
      - '--docker_only=true'
      - '--disable_metrics=percpu,sched,tcp,udp,disk,diskIO,network'
```

### Prometheus + Grafana (VPS3 only)

```yaml
# infrastructure/prometheus/prometheus.yml (Stage 1 targets)
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'giano-app'
    static_configs:
      - targets: ['<VPS1_PUBLIC_IP>:8080']
    metrics_path: '/actuator/prometheus'

  - job_name: 'cadvisor-vps1'
    static_configs:
      - targets: ['<VPS1_PUBLIC_IP>:8090']

  - job_name: 'cadvisor-vps2'
    static_configs:
      - targets: ['<VPS2_PUBLIC_IP>:8090']
```

```yaml
# infrastructure/prometheus/prometheus-ms.yml (Stage 2 targets)
scrape_configs:
  - job_name: 'auth-service'
    static_configs:
      - targets: ['<VPS2_PUBLIC_IP>:8081']
    metrics_path: '/actuator/prometheus'

  - job_name: 'room-service'
    static_configs:
      - targets: ['<VPS2_PUBLIC_IP>:8082']
    metrics_path: '/actuator/prometheus'

  - job_name: 'msg-service'
    static_configs:
      - targets: ['<VPS1_PUBLIC_IP>:8083']
    metrics_path: '/actuator/prometheus'

  - job_name: 'presence-service'
    static_configs:
      - targets: ['<VPS2_PUBLIC_IP>:8084']
    metrics_path: '/actuator/prometheus'

  - job_name: 'cadvisor-vps1'
    static_configs:
      - targets: ['<VPS1_PUBLIC_IP>:8090']

  - job_name: 'cadvisor-vps2'
    static_configs:
      - targets: ['<VPS2_PUBLIC_IP>:8090']
```

**Done when:** Grafana at `http://VPS3_IP:3000` loads. Prometheus targets page shows all scrape targets as UP.

---

## Phase D1 — Stage 1 Deployment

**Prerequisite:** `v1.0-monolith` git tag exists. `ApplicationModuleTest` passing in CI.

### VPS2 — Infrastructure Services

```yaml
# docker-compose.infra.yml (VPS2)
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: giano
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./infrastructure/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "10.0.0.2:5432:5432"    # WireGuard IP only — not public
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giano_user -d giano"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    ports:
      - "10.0.0.2:6379:6379"    # WireGuard IP only

  pgbouncer:
    image: edoburu/pgbouncer:v1.25.1-p0
    restart: unless-stopped
    environment:
      DB_HOST: postgres
      DB_USER: giano_user
      DB_PASSWORD: ${DB_PASSWORD}
      DB_NAME: giano
      POOL_MODE: transaction
      MAX_CLIENT_CONN: 100
      DEFAULT_POOL_SIZE: 25
      AUTH_TYPE: scram-sha-256
      AUTH_USER: giano_user
      AUTH_QUERY: "SELECT usename, passwd FROM pg_shadow WHERE usename=$1"
    ports:
      - "10.0.0.2:6432:5432"    # WireGuard IP only
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
  redis_data:
```

### VPS1 — Application

```yaml
# docker-compose.stage1.yml (VPS1)
services:
  app:
    image: ${CI_REGISTRY_IMAGE}/backend:${IMAGE_TAG}
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://10.0.0.2:6432/giano
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: 10.0.0.2
      SPRING_DATA_REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRY_MINUTES: 15
      JWT_REFRESH_EXPIRY_DAYS: 7
      MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: health,prometheus
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
```

### Deployment Script (Stage 1)

```bash
#!/bin/bash
# scripts/deploy-stage1.sh

set -e

echo "=== Deploying Stage 1 (Monolith) ==="

# 1. Start infrastructure on VPS2
echo "[VPS2] Starting infrastructure..."
ssh deploy@vps2 "
  cd /opt/giano &&
  docker compose -f docker-compose.infra.yml pull &&
  docker compose -f docker-compose.infra.yml up -d &&
  docker compose -f docker-compose.infra.yml ps
"

# 2. Wait for Postgres to be healthy
echo "[VPS2] Waiting for Postgres..."
ssh deploy@vps2 "
  until docker compose -f /opt/giano/docker-compose.infra.yml exec postgres \
    pg_isready -U giano_user -d giano; do sleep 2; done
"

# 3. Deploy app on VPS1
echo "[VPS1] Deploying app..."
ssh deploy@vps1 "
  cd /opt/giano &&
  docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY &&
  docker pull $CI_REGISTRY_IMAGE/backend:latest &&
  docker compose -f docker-compose.stage1.yml up -d
"

# 4. Verify health
echo "[VPS1] Checking health..."
sleep 15
curl -f http://VPS1_PUBLIC_IP:8080/actuator/health

echo "=== Stage 1 deployment complete ==="

# Record operational complexity data point
echo "Stage 1 deployment steps: $(cat $0 | grep -c 'echo \"\[')" >> comparison/stage1/ops-metrics.txt
```

**Record at deployment:**
```
Stage 1 operational complexity:
  Deploy steps:                    ___
  Time from script start → healthy: ___ seconds
  Services in Compose (infra):     3 (postgres, redis, pgbouncer)
  Services in Compose (app):       1 (app)
  Total services:                  4
  Environment variables (total):   ___
  Network config required:         WireGuard (already setup in D0)
  Servers touched:                 2 (VPS1, VPS2)
```

---

## Phase D2 — Stage 2 Deployment

**Prerequisite:** `v2.0-microservices` git tag exists. Stage 1 frozen.

### VPS2 — Per-Service Databases + RabbitMQ

```yaml
# docker-compose.ms-infra.yml (VPS2)
services:
  auth-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: auth_db
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - auth_db_data:/var/lib/postgresql/data
      - ./infrastructure/postgres/auth-init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "10.0.0.2:5433:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giano_user -d auth_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  room-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: room_db
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - room_db_data:/var/lib/postgresql/data
      - ./infrastructure/postgres/room-init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "10.0.0.2:5434:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giano_user -d room_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  msg-db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: msg_db
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - msg_db_data:/var/lib/postgresql/data
      - ./infrastructure/postgres/msg-init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "10.0.0.2:5435:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giano_user -d msg_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3-management-alpine
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: giano
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    ports:
      - "10.0.0.2:5672:5672"
      - "10.0.0.2:15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: redis-server --appendonly yes
    volumes:
      - redis_ms_data:/data
    ports:
      - "10.0.0.2:6380:6379"

  auth-service:
    image: ${CI_REGISTRY_IMAGE}/auth-service:${IMAGE_TAG}
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://auth-db:5432/auth_db
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: giano
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    ports:
      - "10.0.0.2:8081:8080"
    depends_on:
      auth-db:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy

  room-service:
    image: ${CI_REGISTRY_IMAGE}/room-service:${IMAGE_TAG}
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://room-db:5432/room_db
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: giano
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      AUTH_SERVICE_URL: http://10.0.0.2:8081
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    ports:
      - "10.0.0.2:8082:8080"
    depends_on:
      room-db:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy

  presence-service:
    image: ${CI_REGISTRY_IMAGE}/presence-service:${IMAGE_TAG}
    restart: unless-stopped
    environment:
      SPRING_DATA_REDIS_HOST: redis
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: giano
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      AUTH_SERVICE_URL: http://10.0.0.2:8081
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    ports:
      - "10.0.0.2:8084:8080"
    depends_on:
      rabbitmq:
        condition: service_healthy

volumes:
  auth_db_data:
  room_db_data:
  msg_db_data:
  rabbitmq_data:
  redis_ms_data:
```

### VPS1 — Gateway + msg-service

```yaml
# docker-compose.ms-app.yml (VPS1)
services:
  msg-service:
    image: ${CI_REGISTRY_IMAGE}/msg-service:${IMAGE_TAG}
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://10.0.0.2:5435/msg_db
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_RABBITMQ_HOST: 10.0.0.2
      SPRING_RABBITMQ_USERNAME: giano
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      SPRING_DATA_REDIS_HOST: 10.0.0.2
      SPRING_DATA_REDIS_PORT: 6380
      AUTH_SERVICE_URL: http://10.0.0.2:8081
      ROOM_SERVICE_URL: http://10.0.0.2:8082
      MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED: true
    ports:
      - "8083:8080"

  gateway:
    image: nginx:alpine
    restart: unless-stopped
    volumes:
      - ./infrastructure/nginx/gateway.conf:/etc/nginx/nginx.conf:ro
    ports:
      - "80:80"
    depends_on:
      - msg-service
```

### Nginx Gateway Config

```nginx
# infrastructure/nginx/gateway.conf
events { worker_connections 1024; }

http {
  # Upstreams — VPS2 services via WireGuard
  upstream auth    { server 10.0.0.2:8081; }
  upstream room    { server 10.0.0.2:8082; }
  upstream presence { server 10.0.0.2:8084; }

  # msg-service on same host (VPS1)
  upstream msg     { server msg-service:8080; }

  server {
    listen 80;

    location /api/auth/ {
      proxy_pass http://auth/;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
    }

    location /api/rooms/ {
      proxy_pass http://room/;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
    }

    location /api/messages/ {
      proxy_pass http://msg/;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
    }

    location /api/presence/ {
      proxy_pass http://presence/;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket — msg-service
    location /ws/ {
      proxy_pass http://msg/;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection "upgrade";
      proxy_set_header Host $host;
      proxy_read_timeout 3600s;
    }
  }
}
```

**Record at Stage 2 deployment:**
```
Stage 2 operational complexity:
  Deploy steps:                     ___
  Time from script start → healthy:  ___ seconds
  Services in Compose (VPS2):        8 (3 dbs + rabbitmq + redis + 3 services)
  Services in Compose (VPS1):        2 (msg-service + nginx)
  Total services:                    10
  Environment variables (total):     ___
  Network config required:           WireGuard (D0) + per-service firewall rules
  Servers touched:                   2 (VPS1, VPS2)
  New infra vs Stage 1:             RabbitMQ, 3 extra Postgres, WireGuard port rules
```

---

## Phase T1 — Functional Verification (Both Stages)

**Run the QA script from `DEVELOPMENT_PLAN.md` against each stage before running any load test.**

This confirms the two systems are functionally equivalent. If they aren't, load test results are meaningless.

```bash
# Checklist before any load test
[ ] QA script passes against Stage 1 (VPS1:8080)
[ ] QA script passes against Stage 2 (VPS1:80)
[ ] Grafana shows data from correct targets (Stage 1: single app; Stage 2: 4 services)
[ ] All Prometheus targets show UP
[ ] RabbitMQ management UI shows exchanges and queues declared (Stage 2 only)
[ ] outbox.unpublished.count gauge shows 0 at idle
```

---

## Phase T2 — Load Tests

All k6 scenarios run from **VPS3 (Singapore)** against **VPS1 public IP**. Each scenario runs 3 times; median result is used. Raw JSON saved to `comparison/`.

### k6 Prerequisites (VPS3)

```bash
# Install k6
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt update && sudo apt install k6

# Set target
export GIANO_BASE_URL="http://<VPS1_PUBLIC_IP>:8080"   # Stage 1
# or
export GIANO_BASE_URL="http://<VPS1_PUBLIC_IP>:80"     # Stage 2
```

---

### Scenario A — Baseline (Steady 10 VUs, 5 min)

**Purpose:** Establish baseline latency and throughput at low load. p50/p95/p99.

```javascript
// k6/scenarios/baseline.js
import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const e2eLatency = new Trend('giano_msg_e2e_latency', true);
const msgLoss    = new Counter('giano_msg_loss');

export const options = {
  scenarios: {
    baseline: {
      executor: 'constant-vus',
      vus: 10,
      duration: '5m',
    }
  },
  thresholds: {
    'giano_msg_e2e_latency{quantile:0.95}': ['value<300'],
    'http_req_failed': ['rate<0.01'],
  }
};

export function setup() {
  // Create test users and room, return context
  const users = [];
  for (let i = 0; i < 10; i++) {
    const res = http.post(`${__ENV.GIANO_BASE_URL}/api/auth/signup`, JSON.stringify({
      email: `user${i}@test.com`, password: 'Test@1234', displayName: `User${i}`
    }), { headers: { 'Content-Type': 'application/json' } });
    users.push(res.json('accessToken'));
  }
  // Create room with user 0
  const room = http.post(`${__ENV.GIANO_BASE_URL}/api/rooms`, JSON.stringify({
    name: 'load-test-room'
  }), { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${users[0]}` }});
  return { users, roomId: room.json('id') };
}

export default function(data) {
  const token = data.users[__VU % data.users.length];
  const sentAt = Date.now();

  const res = ws.connect(
    `ws://${__ENV.GIANO_BASE_URL.replace('http://', '')}/ws/websocket?token=${token}`,
    {},
    function(socket) {
      socket.on('open', () => {
        socket.send(JSON.stringify({
          destination: `/app/room/${data.roomId}/send`,
          body: JSON.stringify({ text: `msg-${Date.now()}` })
        }));
      });
      socket.on('message', (msg) => {
        e2eLatency.add(Date.now() - sentAt);
        socket.close();
      });
      socket.setTimeout(() => {
        msgLoss.add(1);
        socket.close();
      }, 5000);
    }
  );
  sleep(1);
}
```

**Run:**
```bash
k6 run --out json=comparison/stage1/scenario-a-run1.json k6/scenarios/baseline.js
k6 run --out json=comparison/stage1/scenario-a-run2.json k6/scenarios/baseline.js
k6 run --out json=comparison/stage1/scenario-a-run3.json k6/scenarios/baseline.js
```

---

### Scenario B — Ramp (1 → 50 VUs over 10 min)

**Purpose:** Observe how latency degrades as load increases. Find the knee of the curve.

```javascript
// k6/scenarios/ramp.js
export const options = {
  stages: [
    { duration: '2m', target: 10 },
    { duration: '3m', target: 25 },
    { duration: '3m', target: 50 },
    { duration: '2m', target: 0  },
  ],
  thresholds: {
    'http_req_duration{status:200}': ['p(95)<1000'],
    'http_req_failed': ['rate<0.05'],
  }
};
```

**Run against both stages. Save to:**
```
comparison/stage1/scenario-b-run{1,2,3}.json
comparison/stage2/scenario-b-run{1,2,3}.json
```

---

### Scenario C — Resilience (Kill messaging component mid-run)

**Purpose:** Measure message loss, error spike, and recovery time when the messaging component fails.

**Stage 1 procedure:**
```bash
# Terminal 1: run k6
k6 run --out json=comparison/stage1/scenario-c.json k6/scenarios/resilience.js &

# Terminal 2: wait 90s, then kill the app process (simulate crash)
sleep 90
ssh deploy@vps1 "docker compose -f docker-compose.stage1.yml stop app"

# Wait 30s, then restart
sleep 30
ssh deploy@vps1 "docker compose -f docker-compose.stage1.yml start app"

# k6 records the full window
```

**Stage 2 procedure:**
```bash
# Terminal 1: run k6
k6 run --out json=comparison/stage2/scenario-c.json k6/scenarios/resilience.js &

# Terminal 2: kill only msg-service
sleep 90
ssh deploy@vps1 "docker compose -f docker-compose.ms-app.yml stop msg-service"
# auth-service and room-service on VPS2 remain running

sleep 30
ssh deploy@vps1 "docker compose -f docker-compose.ms-app.yml start msg-service"
```

**k6 script:**
```javascript
// k6/scenarios/resilience.js
// Continuously sends messages for 5 minutes
// Records: message loss count, error rate, latency during outage window

export const options = {
  scenarios: {
    resilience: {
      executor: 'constant-vus',
      vus: 10,
      duration: '5m',
    }
  }
};

// Track error windows for recovery time calculation
```

**What to record:**
```
Stage 1 - component killed:
  Affected functionality:     ALL (entire monolith down)
  Message loss count:         ___
  Error rate spike:           ___% at peak
  Time to detect (k6 errors): ___ ms
  Recovery time:              ___ ms (from restart to first success)
  Auth/room available during outage: NO

Stage 2 - msg-service killed:
  Affected functionality:     Messaging + presence only
  Message loss count:         ___ (outbox ensures 0 if killed cleanly)
  Error rate spike:           ___% at peak
  Time to detect (k6 errors): ___ ms
  Recovery time:              ___ ms
  Auth/room available during outage: YES ← key finding
  Circuit breaker events:     ___
```

---

### Scenario D — Capacity Find (Saturation Point)

**Purpose:** Find the message throughput at which each architecture saturates on this specific hardware.

```javascript
// k6/scenarios/capacity_find.js
import { check } from 'k6';

export const options = {
  scenarios: {
    capacity: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 500,
      stages: [
        { duration: '3m', target: 50  },   // 50 msg/s
        { duration: '3m', target: 100 },
        { duration: '3m', target: 200 },
        { duration: '3m', target: 400 },
        { duration: '2m', target: 0   },
      ],
    }
  },
  thresholds: {
    // Saturation = p95 > 1000ms OR error rate > 5%
    // Don't set these as hard thresholds — just record when they're crossed
  }
};
```

**Run both stages. Identify saturation point from JSON output:**
```
Stage 1 saturation: ___ msg/s (where p95 > 1000ms or error rate > 5%)
Stage 2 saturation: ___ msg/s
```

---

### Scenario E — Independent Deployability

**Purpose:** Quantify deployment downtime for each architecture. This is the clearest practical argument for or against microservices.

**Stage 1 — full redeploy (simulating a bug fix in any module):**
```bash
# k6 runs continuously from VPS3
k6 run --out json=comparison/stage1/scenario-e.json k6/scenarios/baseline.js &

# After 60s, redeploy the monolith
sleep 60
ssh deploy@vps1 "
  docker compose -f docker-compose.stage1.yml pull app &&
  docker compose -f docker-compose.stage1.yml up -d app
"
# k6 records exact window of failed requests
```

**Stage 2 — redeploy only msg-service (simulating a bug fix in messaging):**
```bash
k6 run --out json=comparison/stage2/scenario-e.json k6/scenarios/baseline.js &

sleep 60
ssh deploy@vps1 "
  docker compose -f docker-compose.ms-app.yml pull msg-service &&
  docker compose -f docker-compose.ms-app.yml up -d --no-deps msg-service
"
# Auth and room requests should continue succeeding during msg-service restart
```

**What to record:**
```
Stage 1 deploy:
  Total downtime:                ___ seconds
  Requests failed during deploy: ___
  All endpoints unavailable:     YES

Stage 2 deploy (msg-service only):
  msg-service downtime:          ___ seconds
  /api/auth/** during deploy:    available? YES/NO
  /api/rooms/** during deploy:   available? YES/NO
  WS connections dropped:        ___
  Requests failed (total):       ___
```

---

## Phase T3 — Resource Monitoring

**Run during each load test scenario. cAdvisor feeds into Prometheus automatically.**

**Grafana panels to screenshot at peak load for each scenario:**
```
Per VPS:
  - CPU usage % (per container)
  - Memory usage MB (per container)
  - Network I/O (bytes in/out)

Stage 1 specific:
  - Postgres connection count (pg_stat_activity)
  - PgBouncer pool utilization

Stage 2 specific:
  - RabbitMQ queue depth (room.joined, room.left, message.sent queues)
  - RabbitMQ consumer lag
  - Inter-service HTTP call latency (giano.interservice.call.duration)
  - Circuit breaker state (resilience4j.circuitbreaker.state)
```

**Record at saturation (Scenario D peak):**
```
                    Stage 1     Stage 2
VPS1 CPU peak:      ___%        ___%
VPS1 RAM peak:      ___ MB      ___ MB
VPS2 CPU peak:      ___%        ___%
VPS2 RAM peak:      ___ MB      ___ MB
Total RAM (both):   ___ MB      ___ MB
```

---

## Phase T4 — Comparison Data Collection

**Fill these tables from raw k6 JSON + Grafana screenshots. No editorializing — numbers only.**

### Q1 — Developer Complexity

| Metric | Monolith | Microservices |
|--------|----------|---------------|
| Total backend LOC | | |
| LOC: auth | | |
| LOC: room | | |
| LOC: messaging | | |
| LOC: presence | | |
| Shared library LOC | 0 | |
| Build time cold (single) | | per service |
| Build time wall clock | | parallel |
| Cross-module violations (CI) | 0 | N/A |
| Inter-service HTTP clients | 0 | |
| Circuit breakers added | 0 | |
| Docker images | 1 | 4 |

### Q2 — Operational Complexity

| Metric | Monolith | Microservices |
|--------|----------|---------------|
| Compose services (total) | | |
| Compose config lines | | |
| Environment variables (total) | | |
| Databases | 1 | 4 |
| Message brokers | 0 | 1 |
| Time: deploy from zero → healthy (s) | | |
| Time: redeploy one component (s) | | |
| Downtime during redeploy | full | msg-service only |
| WireGuard / network config steps | 0 | |
| Firewall rules count | | |

### Q3 — Performance

**Scenario A (10 VUs steady):**

| Metric | Monolith | Microservices | Delta |
|--------|----------|---------------|-------|
| p50 latency (ms) | | | |
| p95 latency (ms) | | | |
| p99 latency (ms) | | | |
| Throughput (msg/s) | | | |
| Error rate | | | |
| WS connect time p95 (ms) | | | |
| Inter-service call p95 (ms) | N/A | | |

**Scenario D (saturation):**

| Metric | Monolith | Microservices |
|--------|----------|---------------|
| Saturation point (msg/s) | | |
| p95 at saturation (ms) | | |
| Error rate at saturation | | |
| RabbitMQ queue depth at saturation | N/A | |

### Q4 — Resilience

**Scenario C (kill messaging component):**

| Metric | Monolith | Microservices |
|--------|----------|---------------|
| Messages lost | | |
| Error rate spike | | |
| Auth available during outage | NO | YES |
| Room browse available during outage | NO | YES |
| Recovery time (ms) | | |
| Circuit breaker events | N/A | |
| Outbox: unpublished entries after crash | N/A | |

**Scenario E (redeploy):**

| Metric | Monolith | Microservices |
|--------|----------|---------------|
| Total downtime (s) | | |
| Auth requests failed | | |
| Room requests failed | | |
| Messaging requests failed | | |

---

## Phase T5 — Comparison Report

**File:** `comparison/REPORT.md`

### Report Structure

```markdown
# giano Architecture Comparison Report

## Methodology
- Hardware: VPS1 [spec], VPS2 [spec], VPS3 [spec]
- Network: VPS1↔VPS2 WireGuard (~Xms), VPS3→VPS1 public (~40ms)
- Load tool: k6 vX.X, run from VPS3 (Singapore)
- Metrics: Micrometer → Prometheus (VPS3) → Grafana
- Each scenario run 3 times; median reported
- Monolith git tag: v1.0-monolith
- Microservices git tag: v2.0-microservices

## Results (Q1–Q4 tables — filled from T4)

## Analysis

### What the numbers show
[3–5 sentences per dimension, drawn strictly from data]

### Strongest arguments for microservices (based on this data)
- Independent deployability: ___ seconds downtime vs ___ seconds
- Fault isolation: auth/room available during msg-service outage
- [others from data]

### Strongest arguments against microservices (based on this data)
- Baseline latency +___ms due to inter-service HTTP
- Operational setup: ___ more services, ___ more config lines
- Total RAM overhead: +___ MB at idle
- [others from data]

## What this study cannot claim
- Scale beyond 500 VUs on this hardware was not tested
- Independent team deployability was not measured (single trio)
- Technology heterogeneity benefit was not exercised (all Java)
- Kubernetes-level orchestration benefits are out of scope
- Results are specific to this hardware; saturation points are not general

## Lessons Learned
[What surprised the team during extraction — honest, unfiltered]
```

---

## Operational Complexity Tally Sheet

Maintain this during D0–D2. It feeds directly into Q2.

```
STAGE 1 SETUP LOG
=================
[ ] WireGuard: counted as shared overhead (done once for both stages)
[ ] VPS2 firewall rules: ___ rules added
[ ] VPS1 firewall rules: ___ rules added
[ ] docker-compose.infra.yml: ___ lines
[ ] docker-compose.stage1.yml: ___ lines
[ ] Environment variables: ___ total
[ ] Setup time (D0 + D1): ___ minutes
[ ] Commands run manually: ___

STAGE 2 SETUP LOG
=================
[ ] docker-compose.ms-infra.yml: ___ lines
[ ] docker-compose.ms-app.yml: ___ lines
[ ] gateway.conf: ___ lines
[ ] Additional env vars vs Stage 1: ___
[ ] Extra firewall rules vs Stage 1: ___
[ ] Extra setup steps vs Stage 1: ___
[ ] Setup time (D2 only, not counting D0): ___ minutes
[ ] Commands run manually (D2 only): ___
```

---

## GitLab CI — Deployment Jobs

```yaml
# Additions to .gitlab-ci.yml for Stage 2 builds

docker:build:auth-service:
  stage: build
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
      changes: ["services/auth-service/**", "services/common/**"]
  script:
    - docker build -t $CI_REGISTRY_IMAGE/auth-service:$CI_COMMIT_SHORT_SHA ./services/auth-service
    - docker push $CI_REGISTRY_IMAGE/auth-service:$CI_COMMIT_SHORT_SHA
    - docker tag ... $CI_REGISTRY_IMAGE/auth-service:latest && docker push ...

# Repeat for room-service, msg-service, presence-service
# Change detection per service — only builds what changed
```

**This is itself a Q1/Q2 data point:** CI pipeline builds only the changed service. Monolith always builds the whole thing.

---

## Summary: What Belongs in This Document

| Belongs here | Does NOT belong here |
|-------------|---------------------|
| VPS topology | Business logic |
| WireGuard setup | Entity definitions |
| Docker Compose files | Unit tests |
| Nginx gateway config | API contracts |
| Deployment scripts | Frontend components |
| k6 test scenarios | Micrometer instrumentation code |
| Prometheus/Grafana config | Git module structure |
| Resilience experiment procedures | |
| Comparison data tables | |
| Comparison report template | |
| Operational complexity tally | |

---

**Document Version:** 1.0
**Companion document:** `DEVELOPMENT_PLAN.md`

# Observability — Hướng dẫn dùng Prometheus + Grafana

> Đối tượng: developer hoặc người chấm bài muốn xem hệ đang chạy ra sao trong thời gian thực, chạy benchmark Kafka vs RabbitMQ và xác nhận circuit breaker, rate limit hoạt động bằng số liệu chứ không phải log.

---

## 1. Mở giao diện

| Tool | URL | Login | Mục đích |
|---|---|---|---|
| **Grafana** (dashboard) | http://localhost:3000 | `admin/admin` hoặc anonymous viewer | Xem biểu đồ |
| **Prometheus** (raw) | http://localhost:9090 | — | Query PromQL, debug targets |
| **Kong admin** | http://localhost:8001 | — | Xem plugin + metrics nguồn |
| **RabbitMQ mgmt** | http://localhost:15672 | `guest/guest` (`.env`) | Queue depth, connection list |

Stack được bring up bằng `docker compose up -d` — Prometheus + Grafana là **2 trong số 23 container** trong stack.

---

## 2. Đi tour dashboard "Zalord — Overview"

Mở http://localhost:3000/d/zalord-overview. 10 panel chia 5 hàng:

```
┌──────────────┬──────────────────────────────┬──────────────────┐
│ Services up  │ HTTP req rate per service    │ Kong proxy QPS   │   ← stats
├──────────────┴──────────────┬───────────────┴──────────────────┤
│ HTTP p95 latency by service │ Kong status codes (2xx/4xx/5xx)  │   ← perf
├─────────────────────────────┼──────────────────────────────────┤
│ JVM heap (Java services)    │ Goroutines (Go services)         │   ← resource
├─────────────────────────────┼──────────────────────────────────┤
│ RabbitMQ msgs ready/queue   │ Kafka consumer lag/group         │   ← broker
├─────────────────────────────┴──────────────────────────────────┤
│ Resilience4j circuit breaker state (mediaGrpc, userGrpc)       │   ← resilience
└────────────────────────────────────────────────────────────────┘
```

### 2.1 Cách đọc nhanh từng panel

**Services up** — số scrape target healthy. **12/12** nghĩa là toàn bộ scrape job đang chạy bình thường. Tụt xuống = service hoặc exporter chết.

**HTTP req rate per service** — req/s cộng dồn theo service. Tăng đột biến = traffic spike, giảm xuống 0 = service stopped.

**Kong proxy QPS** — chỉ đếm request đi qua Kong gateway. Khác với "HTTP rate per service" vì internal call (gRPC, projector) không qua Kong.

**HTTP p95 latency** — p95 latency theo service. Mục tiêu: < 100ms cho REST. Bộc lộ ngay khi DB chậm hoặc CPU bị throttle.

**Kong status codes** — phân bố 2xx/4xx/5xx. Lúc rate-limit bật, sẽ thấy spike `429`. Lúc media-service xuống, message-service trả `503` qua Kong → spike `503` ở đây.

**JVM heap** — bytes heap đang dùng/service Java. Mục tiêu < 80% mem_limit. Tăng monoton = memory leak.

**Goroutines** — số goroutine/service Go. Tăng monoton lúc idle = goroutine leak (vd handler không return). chat-service có một goroutine/WS connection → đo lường số online client.

**RabbitMQ msgs ready/queue** — số message đang chờ tiêu thụ trong mỗi queue. > 0 lâu = consumer chậm hơn producer (lag). Quan trọng khi benchmark.

**Kafka consumer lag** — `kafka_consumergroup_lag` per (group, topic). Tương tự RabbitMQ nhưng cho Kafka.

**Resilience4j circuit breaker** — state của mỗi CB (`mediaGrpc`, `userGrpc`). 0 = CLOSED, 1 = OPEN, 2 = HALF_OPEN. Tăng từ 0→1 = CB vừa mở vì downstream chết.

---

## 3. Demo workflow — chứng minh các feature hoạt động bằng số

### 3.1 Chứng minh rate-limit

```bash
# Mở dashboard trước, để mắt panel "Kong status codes"
# Sau đó từ terminal:
for i in {1..20}; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' -d '{"phoneNumber":"x","password":"y"}'
done
```
→ Dashboard: panel "Kong status codes" sẽ thấy line `429` nhảy lên rồi flat trong cả phút sau.

### 3.2 Chứng minh circuit breaker

```bash
# Mở dashboard, mắt vào panel "Resilience4j circuit breaker"
docker compose stop media-service

# Login Alice, lấy token, spam 10 lần POST message với attachment giả
ATOK=$(curl -sS -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"0900111001","password":"secret123"}' | jq -r .data.accessToken)
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/api/v1/messages \
    -H "Authorization: Bearer $ATOK" -H 'Content-Type: application/json' \
    -d '{"conversationId":"<conv-id>","content":"x","mediaIds":["00000000-0000-0000-0000-000000000000"]}'
done
```
→ Sau ~5 request, line `mediaGrpc, state=open` nhảy từ 0 → 1, các request sau trả `503` ngay (không gọi media nữa). Khôi phục:
```bash
docker compose start media-service
# Đợi 30s, CB tự half-open rồi closed → line về 0
```

### 3.3 Chứng minh broker lag

Mở 2 panel: "HTTP req rate per service" + "RabbitMQ msgs ready/queue". Chạy:
```bash
PROFILE=ramp scripts/run-loadtest.sh
```
- "HTTP req rate" trên `message-service` ramp từ 50 → 2000 RPS trong 7 phút
- Đồng thời "msgs ready" cho `chat-fanout.queue` / `notification-message.queue` tăng vọt rồi giảm dần khi consumer bắt kịp → minh hoạ outbox + broker lag.

---

## 4. PromQL — vài query hay dùng

Mở http://localhost:9090/graph → tab Graph → dán expression:

| Cần biết | PromQL |
|---|---|
| Throughput per service | `sum by (service) (rate(http_server_requests_seconds_count[1m]))` |
| p95 latency per service | `histogram_quantile(0.95, sum by (le, service) (rate(http_server_requests_seconds_bucket[1m])))` |
| Error rate (5xx) per service | `sum by (service) (rate(http_server_requests_seconds_count{status=~"5.."}[1m]))` |
| Kong 4xx vs 5xx | `sum by (code) (rate(kong_http_requests_total{code=~"[45].."}[1m]))` |
| JVM heap usage ratio | `sum by (service) (jvm_memory_used_bytes{area="heap"}) / sum by (service) (jvm_memory_max_bytes{area="heap"})` |
| Goroutines per Go svc | `go_goroutines` |
| RabbitMQ message rate per queue | `rate(rabbitmq_queue_messages_published_total[1m])` |
| Kafka producer rate | `rate(kafka_topic_partition_current_offset[1m])` |
| Kafka consumer lag | `sum by (consumergroup, topic) (kafka_consumergroup_lag)` |
| CB calls failed | `rate(resilience4j_circuitbreaker_calls_seconds_count{kind="failed"}[1m])` |
| CB current state | `resilience4j_circuitbreaker_state == 1` (chỉ hiện khi OPEN) |

---

## 5. Load test với k6 — Kafka vs RabbitMQ

Toàn bộ benchmark dùng [k6](https://k6.io/) (Go-based load tester) chạy trong **Docker container** trên cùng network với compose stack — không cần install gì trên host ngoài Docker + `jq`.

### 5.1 File liên quan

| File | Vai trò |
|---|---|
| `scripts/loadtest.js` | k6 script: define 3 profile (smoke / sustained / ramp), POST `/api/v1/messages`, ghi JSON summary |
| `scripts/run-loadtest.sh` | Wrapper bash: bootstrap Alice/Bob + conv → loop từng backend → recreate 3 service với `EVENT_BUS=<backend>` → chạy k6 → repeat |
| `scripts/loadtest-<backend>-<profile>.json` | Output: full k6 metrics dump (per backend), regenerate mỗi lần chạy |

### 5.2 Prerequisite

```bash
# Lần đầu — pull k6 image (~30MB)
docker pull grafana/k6:latest
brew install jq                                # macOS, hoặc apt-get install jq trên Linux

# Stack phải up + Alice (0900111001) + Bob (0900111002) đã register
make dev
# Nếu chưa có user, register thủ công qua /api/v1/auth/register hoặc chạy seed script
```

### 5.3 Ba profile

| Profile | Pattern | RPS | Thời gian | Tổng req | Dùng khi |
|---|---|---|---|---|---|
| `smoke` | `constant-arrival-rate` | 50 | 30s | 1.5K | Sanity check sau khi sửa code |
| **`sustained`** *(default)* | `constant-arrival-rate` | 500 | 5 phút | 150K | **Số liệu chính cho thesis** — steady-state |
| `ramp` | `ramping-arrival-rate` | 50 → 2000 | 7 phút | ~400K | Tìm điểm gãy (error rate vọt / throughput sụp) |

Lý do dùng `constant-arrival-rate` chứ không `constant-vus`:
- VUs mode = "luôn có N user gửi tuần tự" → throughput thay đổi theo latency, khó so sánh fair
- Arrival rate mode = "**đảm bảo X RPS bất kể latency**" → giống traffic prod thật, latency phản ánh đúng load

### 5.4 Cách chạy

```bash
# Mặc định: profile=sustained, cả 2 backend
scripts/run-loadtest.sh

# Profile khác
PROFILE=smoke     scripts/run-loadtest.sh     # 30s × 2 backend = ~1.5 phút
PROFILE=sustained scripts/run-loadtest.sh     # 5 phút × 2 backend + recreate = ~12 phút
PROFILE=ramp      scripts/run-loadtest.sh     # 7 phút × 2 backend + recreate = ~16 phút

# Chỉ 1 backend
BACKENDS=kafka scripts/run-loadtest.sh
BACKENDS=rabbitmq scripts/run-loadtest.sh

# Cả 2 ở smoke
BACKENDS="rabbitmq kafka" PROFILE=smoke scripts/run-loadtest.sh
```

### 5.5 Wrapper làm gì step-by-step

1. **Bootstrap** (5–10s): login Alice + Bob qua Kong (retry 5 lần nếu 502/429), lấy user-id, tạo DIRECT conv (gọi thẳng `message-service:8083` để né Kong flakiness)
2. **Loop từng backend**:
   - `EVENT_BUS=<backend> docker compose up -d --force-recreate --no-deps message-service chat-service notification-service` (~30s downtime)
   - Wait 3 service /health OK (timeout 90s)
   - Sleep 3s cho consumer subscribe xong
3. **Chạy k6**:
   ```bash
   docker run --rm -i \
     --network zalord_zalord-net \              # vào chung network compose
     --user $(id -u):$(id -g) \                  # avoid file perm issue
     -v $(pwd)/scripts:/scripts -w /scripts \
     -e UID=$UID_A -e CONV=$CONV \
     -e BACKEND=$BACKEND -e PROFILE=$PROFILE \
     -e TARGET=http://message-service:8083/api/v1/messages \
     grafana/k6:latest run /scripts/loadtest.js
   ```
4. **Output**: JSON summary tự ghi vào `scripts/loadtest-<backend>-<profile>.json` (k6 `handleSummary` hook)

### 5.6 Kết quả mẫu — sustained 500 RPS × 5 phút

Đã chạy trên VPS dev local (M1 Mac, Docker Desktop, 8GB RAM allocated):

| Backend | Requests | Throughput | Error rate | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| **RabbitMQ** | 149,957 | 500/s | 0.00% | 0.81ms | 2.20ms | **9.26ms** | 453.3ms |
| **Kafka** | 150,000 | 500/s | 0.00% | 0.84ms | 1.76ms | **6.25ms** | 212.0ms |

### 5.7 Cách đọc

- **p50 ngang nhau** (~0.8ms) — happy path identical, broker write không phải bottleneck ở 500 RPS
- **p95 Kafka thắng 20%** (1.76 vs 2.20ms) — Kafka batch write ổn định hơn dưới load đều
- **p99 Kafka thắng 32%** (6.25 vs 9.26ms) — quan trọng nhất cho SLO, Kafka tail latency tốt hơn rõ rệt
- **max Kafka thắng 53%** (212 vs 453ms) — RabbitMQ có spike GC/flush rare nhưng to gấp đôi
- **0 error cả 2** — outbox + Postgres connection pool đủ headroom ở 500 RPS
- **500 RPS = 30K msg/phút = 1.8M msg/giờ** → đủ vượt target 10K concurrent user (mỗi user ~1 msg/phút)

### 5.8 Khi nào nên dùng cái nào

| Tình huống | Chọn | Lý do (theo số liệu) |
|---|---|---|
| Production target ≥10K user, ưu tiên UX | **Kafka** | Tail latency p99 ổn định hơn 32% |
| Dev/staging, ưu tiên ops đơn giản | **RabbitMQ** | 1 UI quản lý, ít moving parts |
| Cần partition/consumer rebalance | **Kafka** | Built-in, Rabbit phải tự sharding queue |
| Cần routing phức tạp (topic exchange) | **RabbitMQ** | Native, Kafka phải làm app-side |

Trong Zalord: **switchable** qua env `EVENT_BUS`. Default `rabbitmq` cho dev, deploy `kafka` cho prod.

### 5.9 Xem trong Grafana lúc chạy

Mở [http://localhost:3000/d/zalord-overview](http://localhost:3000/d/zalord-overview) trước khi launch k6. Panel cần để mắt:

| Panel | Quan sát gì |
|---|---|
| **HTTP req rate per service** | `message-service` ramp lên ~500 RPS (sustained) hoặc 50→2000 RPS (ramp). Phase 1 = rabbit, recreate gap, phase 2 = kafka |
| **HTTP p95 latency** | So sánh trực quan 2 phase. Kafka phase đường thấp hơn = tail latency tốt hơn |
| **Kong status codes** | Vẫn flat (k6 bypass Kong) — nếu thấy spike ở đây = có request rò qua Kong |
| **RabbitMQ msgs ready/queue** | Phase rabbit: queue depth dao động nhẹ → consumer kịp. Nếu tăng đều = consumer là bottleneck |
| **Kafka consumer lag** | Tương tự cho phase kafka |
| **JVM heap (message-service)** | Coi có GC spike không — RabbitMQ max 453ms có thể do GC pause |

> **Slide thesis tip**: screenshot panel "HTTP req rate" + "p95 latency" trong lúc full sustained run — chart 2 phase liên tiếp trên cùng trục thời gian là minh hoạ thuyết phục nhất cho "Kafka có tail latency tốt hơn".

### 5.10 Phân tích JSON output

```bash
# Quick compare cả 2 file
for f in scripts/loadtest-*-sustained.json; do
  echo "=== $(basename $f) ==="
  jq '{
    requests: .metrics.iterations.values.count,
    rps:      .metrics.iterations.values.rate,
    err_pct:  (.metrics.http_req_failed.values.rate * 100),
    p50_ms:   .metrics.http_req_duration.values.med,
    p95_ms:   .metrics.http_req_duration.values["p(95)"],
    p99_ms:   .metrics.http_req_duration.values["p(99)"],
    max_ms:   .metrics.http_req_duration.values.max
  }' "$f"
done
```

Full schema xem [k6 metrics docs](https://k6.io/docs/using-k6/metrics/).

### 5.11 Troubleshoot benchmark

| Triệu chứng | Nguyên nhân | Fix |
|---|---|---|
| `curl: (22) error 502` ở bootstrap | Kong → message-service flaky lúc cold | Script đã có retry 5 lần. Nếu vẫn fail: `make logs SERVICE=message-service` |
| `curl: (22) error 429` ở bootstrap | Hit Kong rate-limit auth route (10/phút) sau khi chạy benchmark nhiều lần | Đợi 60s rồi retry |
| `service never came healthy` | message/chat/notification crash sau recreate | Check logs từng service, thường là RabbitMQ chưa ready hoặc DB connection fail |
| Throughput thấp bất thường (<100/s) | k6 không bypass Kong → bị rate-limit | Verify `TARGET` env trong `loadtest.js` là `http://message-service:8083`, không phải Kong URL |
| `permission denied` ghi JSON | k6 container chạy sai user | Wrapper đã `--user $(id -u):$(id -g)` — nếu vẫn lỗi check ownership của `scripts/` |
| Error rate > 1% | Backend overload | Giảm RPS trong `loadtest.js` hoặc kiểm tra DB pool size, RabbitMQ memory |

---

## 6. Mở rộng — thêm metric mới

### 6.1 Java service

1. Inject `MeterRegistry`:
   ```java
   @Autowired MeterRegistry registry;
   ```
2. Tạo metric:
   ```java
   Counter c = Counter.builder("message_sent_total")
       .tag("type", "DIRECT")
       .register(registry);
   c.increment();
   ```
3. Tự động xuất hiện ở `/actuator/prometheus` → Grafana query được ngay.

### 6.2 Go service

1. Add collector (gọi 1 lần lúc init):
   ```go
   var sent = promauto.NewCounterVec(
       prometheus.CounterOpts{Name: "message_pushed_total"},
       []string{"type"})
   ```
2. Increment trong handler:
   ```go
   sent.WithLabelValues("ws").Inc()
   ```
3. Tự động xuất hiện ở `/metrics`.

### 6.3 Thêm panel Grafana

- Mở dashboard → ⚙ Settings → Add panel → chọn Prometheus datasource
- Paste PromQL query (xem mục 4 cho format)
- Save dashboard → JSON tự update ở `infra/grafana/dashboards/zalord-overview.json` (vì `allowUiUpdates: true`)

---

## 7. Troubleshooting

| Triệu chứng | Cách xử lý |
|---|---|
| Panel Grafana hiển thị "No data" | Vào http://localhost:9090/targets — target có DOWN không? Nếu yes: kiểm tra service container lên chưa. |
| Target DOWN với "connection refused" | Service chưa lên hoặc port mapping sai trong `prometheus.yml`. Verify bằng `curl http://<svc>:<port>/metrics` từ trong Prometheus container: `docker compose exec prometheus wget -qO- http://message-service:8083/actuator/prometheus`. |
| Query trả gì cũng `0` | Service mới khởi động, metric chưa được trigger lần nào (vd CB chưa call lần nào → state series chưa register). Gửi 1 request xong reload. |
| Grafana mất dashboard sau restart | Provisioning file ở `infra/grafana/provisioning/dashboards/dashboards.yaml` có sai path không? Đường dẫn JSON phải khớp với volume mount trong `docker-compose.yml`. |
| Đổi `prometheus.yml` mà không thấy effect | `docker compose restart prometheus` (hot reload `POST /-/reload` có thể fail nếu YAML syntax lạ). |
| Sau khi đổi `kong.yml` không thấy plugin mới | `docker compose restart kong` — Kong substitute `__JWT_SECRET__` chỉ chạy ở startup. |

---

## 8. RAM footprint

| Container | mem_limit | Đo thực |
|---|---|---|
| prometheus | 256m | ~80MB ở 7-day retention |
| grafana | 192m | ~70MB |
| kafka-exporter | 64m | ~12MB |
| rabbitmq_prometheus plugin | (trong rabbitmq) | ~5MB |

Tổng phụ thêm: ~250MB. VPS 8GB còn thừa khoảng 1.1GB sau khi load đầy đủ stack.

---

## 9. Production hardening checklist (KHÔNG làm trong dev)

- [ ] Đổi Grafana admin password (`GF_SECURITY_ADMIN_PASSWORD`)
- [ ] Tắt `GF_AUTH_ANONYMOUS_ENABLED`
- [ ] Đóng port Prometheus 9090 + Kong admin 8001 không expose ra public
- [ ] Sealed-secrets cho `JWT_SECRET`, `*_PASSWORD` thay vì `.env`
- [ ] Dài hạn: chuyển Prometheus sang **remote_write** (Mimir, Thanos) — local TSDB không scale > 1 instance
- [ ] Thêm alertmanager + alert rule cho:
  - `up == 0` trong 1 phút
  - `histogram_quantile(0.95, ...) > 0.5` (p95 > 500ms)
  - `resilience4j_circuitbreaker_state == 1` (CB open)
  - `rabbitmq_queue_messages_ready > 1000` (queue building up)

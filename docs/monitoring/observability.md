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
RATE=500 DURATION=120 scripts/run-benchmark-rabbit.sh
```
- "HTTP req rate" trên `message-service` lên ~500 RPS sustained 2 phút
- Đồng thời "msgs ready" cho `chat-fanout.queue` / `notification-message.queue` tăng vọt rồi giảm dần khi consumer bắt kịp → minh hoạ outbox + broker lag

→ Chi tiết load test xem [docs/benchmark.md](benchmark.md).

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

## 5. Load test — Kafka vs RabbitMQ

> **Doc đầy đủ**: [docs/benchmark.md](benchmark.md). Phần này chỉ tóm tắt và trỏ panel cần xem khi chạy.

Benchmark đo **end-to-end**: Alice POST → outbox → broker → consumer → WS push → Bob nhận. Đó là cái user cảm nhận, cũng là cái phản ánh đúng broker (POST thuần không chạm broker — xem [docs/patterns.md](../patterns.md) Outbox).

### 5.1 Quick start

```bash
make dev                                    # stack up
# register Alice (0900111001) + Bob (0900111002) lần đầu — xem benchmark.md §4

# Chạy 2 backend back-to-back, append vào cùng JSON
RESET=1 RATE=200 DURATION=120 scripts/run-benchmark-rabbit.sh \
 &&     RATE=200 DURATION=120 scripts/run-benchmark-kafka.sh
```

4 file:
- `scripts/benchmark-e2e.py` — Python engine (WS subscribe + HTTP sender + DB lag query)
- `scripts/run-benchmark-rabbit.sh` — wrapper riêng cho RabbitMQ
- `scripts/run-benchmark-kafka.sh` — wrapper riêng cho Kafka
- `scripts/benchmark-results.json` — kết quả append, mỗi run +1 row

### 5.2 3 metric quan trọng (chi tiết §2 của benchmark.md)

| Metric | Phản ánh |
|---|---|
| **POST p95** | Postgres + media-gRPC, **không** liên quan broker |
| **OUTBOX_LAG p95** | Poll wait (avg = POLL_MS/2) + broker publish thuần |
| **DELIVERY p95** | End-to-end user cảm nhận |
| **delivery_rate** | Phải = 100%, < 100% là bug |

### 5.3 Panel cần để mắt khi chạy benchmark

Mở dashboard trước, mỗi run sẽ thấy 2 phase trên trục thời gian (rabbit → ~30s gap recreate → kafka).

| Panel | Quan sát |
|---|---|
| **HTTP req rate** | `message-service` line lên đúng `RATE` env |
| **HTTP p95 latency** | `message-service` ~5-10ms (POST). Khác biệt rõ với e2e ~60ms |
| **RabbitMQ msgs ready/queue** | Phase rabbit: dao động 0-50 = consumer kịp. Tăng đều = bottleneck |
| **Kafka consumer lag** | Tương tự cho phase kafka |
| **JVM heap (message-service)** | GC spike to lúc benchmark = ảnh hưởng OUTBOX_LAG tail |
| **CB state** | Phải = 0. Nhảy 1 = media-service trip → số liệu lệch |

Chi tiết PromQL + screenshot strategy: xem [docs/benchmark.md §7](benchmark.md#7-quan-sát-trong-grafana-lúc-chạy).

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

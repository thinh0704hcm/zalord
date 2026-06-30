# Distributed Tracing — OpenTelemetry + Jaeger

> Doc này hướng dẫn dùng tracing để **theo dõi 1 request đi qua nhiều service** trong hệ Zalord. Khác với metrics (count + latency theo service riêng lẻ ở [observability.md](./observability.md)), tracing cho phép nhìn **timeline chi tiết từng span** của 1 trace cụ thể.

---

## Phần 1 — Tracing là gì? Khác metrics chỗ nào?

### 1.1 Vấn đề mà metrics không trả lời được

Grafana cho biết "message-service p99 latency = 60ms" — nhưng không biết **60ms đó tiêu vào đâu**:
- Postgres INSERT mất bao lâu?
- gRPC call sang media-service mất bao lâu?
- Kong plugin (JWT auth, rate-limit) tốn bao nhiêu?
- Hibernate generate query SQL tốn bao nhiêu?

Với tracing, mỗi request được ghi lại như 1 cây span — mỗi span = 1 đoạn việc, có start/end timestamp và parent-child relationship.

### 1.2 Trace + Span — vocabulary

| Khái niệm | Ý nghĩa |
|---|---|
| **Trace** | 1 request từ đầu tới cuối, gồm nhiều span. Có `traceID` 16 byte hex |
| **Span** | 1 đoạn việc cụ thể (1 HTTP call, 1 DB query, 1 plugin) trong trace. Có `spanID` |
| **Parent-child** | Span con thuộc span cha. Cha kết thúc khi mọi con kết thúc |
| **Context propagation** | Cách trace_id được truyền qua boundary (HTTP header `traceparent`, broker header `traceparent`) |

Ví dụ 1 POST /messages tạo cây span như sau:

```
kong (root, 7ms)
├── kong.access.plugin.cors        (0.002ms)
├── kong.access.plugin.jwt         (0.076ms)
├── kong.access.plugin.rate-limit  (0.188ms)
└── POST /api/v1/messages (message-service, 5.1ms)
    ├── SELECT conversation_members (Postgres, 0.27ms)
    ├── INSERT messages              (Postgres, 0.26ms)
    ├── Session.persist OutboxEvent  (Postgres, 0.06ms)
    └── Transaction.commit           (Postgres, 1.47ms)
```

→ Đọc xuống thấy ngay "phần lớn 7ms là tx commit + Postgres select". JWT auth chỉ 0.076ms = rate-limit chỉ 0.188ms = plugin không phải bottleneck.

---

## Phần 2 — Cái gì đã được wire sẵn

### 2.1 Stack tracing

```
┌─────────────────┐    OTLP gRPC      ┌──────────────────┐
│ Service         │  ────────────►    │ otel-collector   │
│ (Java/Go/Kong)  │   :4317           │ (port 4317)      │
└─────────────────┘                   └─────┬────────────┘
                                            │ OTLP gRPC
                                            ▼ :4317
                                      ┌──────────────────┐
                                      │ Jaeger (UI 16686)│
                                      └──────────────────┘
```

| Component | Container | Vai trò |
|---|---|---|
| **otel-collector** | `zalord-otel-collector` | Nhận trace từ mọi service qua OTLP gRPC port 4317. Forward sang Jaeger. Cũng exports metrics sang Prometheus port 8889 (cùng pipe) |
| **Jaeger all-in-one** | `zalord-jaeger` | Backend lưu trace + UI port 16686. Dev mode in-memory (mất khi restart) |
| **Config** | [infra/otel/otelcol-config.yaml](../../infra/otel/otelcol-config.yaml) | Pipeline: receivers (OTLP) → processors (batch) → exporters (Jaeger + Prometheus) |

### 2.2 Services đã instrument

| Service | Cách instrument |
|---|---|
| **Java (auth, message, media, group)** | OTel Java Agent (auto-instrumentation). Dockerfile pulls `opentelemetry-javaagent.jar`. `JAVA_OPTS=-javaagent:/app/...` trong [docker-compose.yml](../../docker-compose.yml). Bắt tự động: Spring MVC, JDBC, Hibernate, gRPC, RabbitMQ |
| **Go (user, chat, notification)** | OTel SDK manual + middleware. `otelgin.Middleware("chat-service")` ở [chat-service/cmd/server/main.go:102](../../backend/chat-service/cmd/server/main.go#L102). Tự động bắt Gin HTTP + gRPC |
| **Kong gateway** | Plugin `opentelemetry` config trong [infra/kong/kong.yml](../../infra/kong/kong.yml). Env `KONG_TRACING_INSTRUMENTATIONS=all KONG_TRACING_SAMPLING_RATE=1.0` ở [docker-compose.yml](../../docker-compose.yml). Bắt mọi plugin (jwt, rate-limit, cors...) |
| **RabbitMQ propagation** | Java agent tự bơm `traceparent` header vào message. Consumer side (chat-service, notification-service) parse header → span con của producer → cross-service trace **đi qua broker** |

### 2.3 Sampling

- **`KONG_TRACING_SAMPLING_RATE=1.0`** = 100% — mọi request đều được trace
- Production thật thường set 0.1-1% (10K trace/s đẩy vào Jaeger memory sẽ OOM)
- Dev/thesis: giữ 1.0 để demo

---

## Phần 3 — Mở Jaeger UI

URL: **http://localhost:16686**

### 3.1 Layout

```
┌────────────────────────────────────────────────────────┐
│ Search │ Compare │ System Architecture │ Monitor       │   ← top tabs
├────────────────────────────────────────────────────────┤
│ Service: [auth-service ▼]                              │
│ Operation: [all ▼]                                     │
│ Tags: [http.status_code=500]                           │   ← filter
│ Lookback: [Last hour ▼]                                │
│ Min/Max duration: [_____] [_____]                      │
│ Limit Results: [20]                                    │
│ [Find Traces]                                          │
├────────────────────────────────────────────────────────┤
│ ▼ Traces ranked by duration                            │
│ ┌──────────────────────────────────────────────────┐   │
│ │ auth-service: POST /api/v1/auth/login   15.2ms  │   │
│ │ ├ 12 spans, 2 services                          │   │   ← click vào row
│ │ ├ Tags: http.status=200, ...                    │   │     mở detail view
│ └──────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────┘
```

### 3.2 Tìm trace cụ thể

1. Chọn service ở dropdown — vd `message-service`
2. (optional) Chọn operation — vd `POST /api/v1/messages`
3. Set lookback "Last hour"
4. Bấm **"Find Traces"**

→ Danh sách trace recent. Sort theo duration để tìm tin chậm nhất.

### 3.3 Đọc trace detail

Bấm 1 trace → mở waterfall view:

```
Trace Timeline
─────────────────────────────────────────────────────
  0ms                                          7ms
│                                              │
├─ kong [kong-gateway]                  ████████ 7.087ms (root)
│  ├─ kong.access.plugin.cors           ▏        0.002ms
│  ├─ kong.access.plugin.jwt            ▎        0.076ms
│  ├─ kong.access.plugin.rate-limiting  ▎        0.188ms
│  └─ POST /api/v1/messages [message-service] █████ 5.151ms
│     ├─ SELECT conversation_members [pg] █     0.272ms
│     ├─ INSERT messages [pg]           █       0.262ms
│     ├─ Session.persist OutboxEvent    ▏       0.062ms
│     └─ Transaction.commit             ██      1.472ms
```

- **Trục ngang**: thời gian (0ms tới end)
- **Bar dài**: span lâu
- **Indent**: parent-child
- **Color**: theo service (Kong xanh, message-service vàng...)

Bấm 1 span → mở panel phải hiện:
- **Tags** (`http.method`, `http.status_code`, `db.statement` cho query SQL)
- **Process info** (service name, host, container)
- **Logs** (event timestamps trong span)

---

## Phần 4 — Tracing 4 flow chính của Zalord

### 4.1 Flow đăng nhập — Kong → auth-service → Postgres

```
Service: auth-service
Operation: POST /api/v1/auth/login
```

Trace điển hình:
```
kong (10ms root)
├─ kong.access.plugin.cors
├─ kong.access.plugin.rate-limiting  ← nếu hit, sẽ thấy 429 ở status
└─ POST /api/v1/auth/login (auth-service, 8ms)
   ├─ SELECT users WHERE phone_number=?
   ├─ PasswordEncoder.matches            ← bcrypt, có thể chiếm 50%+ thời gian
   └─ Token.sign (JWT HMAC)
```

### 4.2 Flow gửi tin — Kong → message-service → outbox (full path xem §1.2)

Đã example ở Phần 1.2. Lưu ý:
- Không thấy span "broker publish" trong trace POST này — vì publish nằm ở OutboxScheduler, trace riêng

### 4.3 Flow async publish — OutboxScheduler → broker → consumer (cross-service qua broker)

```
Service: message-service
Operation: OutboxScheduler.publishPending
```

Trace điển hình (khi `OUTBOX_POLL_MS=50`):
```
OutboxScheduler.publishPending (message-service, 30ms root)
├─ SELECT outbox_events WHERE published_at IS NULL FOR UPDATE
├─ exchange.publish [RabbitMQ]                      ← chỗ này trace_id được bơm vào header
└─ UPDATE outbox_events SET published_at = ...

   ╔══════════════════════════════════════════╗
   ║ TRACE TIẾP TỤC SANG CONSUMER (broker)    ║
   ╚══════════════════════════════════════════╝
   ↓
chat-fanout consumer (chat-service, 5ms)
├─ json.Unmarshal
└─ registry.Get(recipient).Send <- frame
   (WebSocket push không trace được vì là long-lived connection)
   
notification consumer (notification-service, 3ms)
└─ ... 
```

**Đây là sức mạnh thật của distributed tracing**: 1 trace `e386a24e...` chứa span từ **cả 3 service** (message + chat + notification) dù chúng nói chuyện qua RabbitMQ.

### 4.4 Flow gRPC — message-service gọi media-service

Khi gửi tin có attachment:
```
POST /api/v1/messages (message-service)
└─ ValidateMedia [gRPC media-service]
   └─ MediaService/Validate handler (media-service)
      └─ SELECT media WHERE id IN (...)
```

→ Span gRPC bắc cầu 2 service, có cả client side (caller) lẫn server side (callee).

---

## Phần 4.5 — Recipe: trace 1 URL cụ thể từ A tới Z

**Câu hỏi**: "Tôi có 1 URL `http://host:port/path` — muốn biết request này đi qua những service nào, mỗi service tốn bao lâu, có gọi DB nào không."

**Cách làm** (3 lệnh):

### Step 1: Inject trace_id biết trước vào request

```bash
# Generate trace_id (16 byte hex) + initial span_id (8 byte hex)
TRACE_ID=$(openssl rand -hex 16)
SPAN_ID=$(openssl rand -hex 8)

# Bắn request với header `traceparent` chuẩn W3C
# Format: 00-{trace_id}-{span_id}-01   (01 = sampled = chắc chắn được export)
curl -H "traceparent: 00-${TRACE_ID}-${SPAN_ID}-01" \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -X POST "http://localhost:8080/api/v1/messages" \
     -d '{"conversationId":"...","content":"hello"}'

echo "trace_id = ${TRACE_ID}"
```

Lưu lại `TRACE_ID`. **Mọi service trên đường đi sẽ adopt trace_id này** — Kong plugin OTel, Java agent, Go middleware, gRPC interceptor, RabbitMQ propagation đều respect W3C `traceparent`. **Không cần đổi code backend** — hệ đã wire sẵn.

### Step 2: Đợi 5 giây cho otel-collector flush batch

```bash
sleep 5
```

(OTel collector batch interval mặc định 5s. Không đợi đủ thì trace mới chưa xuất hiện trong Jaeger.)

### Step 3: Mở trace trong Jaeger

```bash
# Cách 1: mở browser thẳng vào trace detail
open "http://localhost:16686/trace/${TRACE_ID}"

# Cách 2: lấy JSON nếu chỉ cần inspect nhanh
curl -s "http://localhost:16686/api/traces/${TRACE_ID}" \
  | jq '.data[0].spans | map({op: .operationName, svc: .processID, dur_ms: (.duration/1000)})'
```

### Step 4: Đọc waterfall view

UI hiện cây span đầy đủ. Ví dụ cho `POST /api/v1/messages`:

```
kong (kong-gateway)                            ████████ 7.087ms
├─ kong.access.plugin.cors                     ▏        0.002ms
├─ kong.access.plugin.jwt                      ▏        0.076ms       ← JWT validate
├─ kong.access.plugin.rate-limiting            ▎        0.188ms       ← rate-limit check
└─ POST /api/v1/messages (message-service)     █████    5.151ms
   ├─ ValidateMedia [gRPC media-service]       ▎        0.412ms       ← gọi sang media-service
   │  └─ MediaService/Validate (media-service) ▎        0.301ms       ← bên media handle
   │     └─ SELECT media WHERE id IN (...)     ▏        0.089ms
   ├─ SELECT conversation_members              ▎        0.272ms       ← Postgres query
   ├─ INSERT messages                          ▎        0.262ms
   ├─ INSERT outbox_events                     ▏        0.062ms
   └─ Transaction.commit                       █        1.472ms
```

→ **Đọc xuống thấy ngay**: request đi qua **3 service** (kong-gateway → message-service → media-service via gRPC), tốn 7ms tổng, bottleneck là `Transaction.commit` (1.5ms) + Kong overhead.

### Step 5: Bonus — trace tiếp phần async (sau khi POST trả response)

POST `/messages` chỉ ghi outbox + trả 201 → broker được publish sau bởi `OutboxScheduler` (background thread khác). **Trace này không cùng trace_id** với POST của bạn vì OutboxScheduler tự sinh trace_id mới khi nó chạy.

Để xem phần async (publish lên broker → consumer ở chat-service/notification-service nhận):

```bash
# Tìm trace OutboxScheduler.publishPending trong cùng cửa sổ thời gian
curl -s "http://localhost:16686/api/traces?service=message-service&operation=OutboxScheduler.publishPending&limit=5" \
  | jq '[.data[] | {traceID, services: [.processes|to_entries[]|.value.serviceName]|unique, n: (.spans|length)}] | sort_by(-.n) | .[0]'
```

Hoặc trong UI: chọn service `message-service` → operation `OutboxScheduler.publishPending` → lookback 5 phút. Trace nào có nhiều span (>10) và services include `chat-service`/`notification-service` = trace có propagation broker.

### Helper function — tự động hoá Step 1+2+3

Bỏ vào `~/.zshrc` hoặc `~/.bashrc`:

```bash
trace_curl() {
  local tid=$(openssl rand -hex 16)
  local sid=$(openssl rand -hex 8)
  echo "→ trace_id=$tid"
  curl -H "traceparent: 00-$tid-$sid-01" "$@"
  echo
  echo "→ waiting 5s for OTel collector flush..."
  sleep 5
  echo "→ open: http://localhost:16686/trace/$tid"
}
```

Rồi dùng:

```bash
trace_curl -X POST "http://localhost:8080/api/v1/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"...","content":"hi"}'
```

Output:
```
→ trace_id=a1b2c3d4e5f6789012345678abcdef12
{"status":"success", ...}
→ waiting 5s for OTel collector flush...
→ open: http://localhost:16686/trace/a1b2c3d4e5f6789012345678abcdef12
```

Click link → mở Jaeger UI ngay → thấy full waterfall.

### Áp dụng cho URL bất kỳ (kể cả không phải Zalord)

Pattern giống nhau cho mọi URL có OTel wired backend:

```bash
trace_curl -X GET "http://host:port/any/path/you/want"
trace_curl -X POST "http://host:port/chat/{chatboxid}/message/content" -d '...'
trace_curl "http://localhost:8080/api/v1/users/me" -H "Authorization: Bearer $TOKEN"
```

→ Mọi request đều show trace tree đầy đủ. Đây là **cách duy nhất reliable** để truy lùng "request này đi qua đâu" — search by tag dễ miss nếu nhiều request đồng thời.

### Troubleshoot recipe

| Triệu chứng | Nguyên nhân + fix |
|---|---|
| Browser mở Jaeger nhưng "Trace not found" | (1) Chưa đủ 5s, đợi thêm. (2) Service chưa instrument OTel — check Phần 2.2. (3) Header `traceparent` sai format — phải đúng 16+8 byte hex |
| Thấy trace nhưng span ít, chỉ 1-2 service | (1) Service không propagate context — check service code có middleware OTel chưa. (2) Có call HTTP/gRPC dùng plain client không qua wrapper → không có propagation |
| Trace của POST không có phần broker/consumer | Đó là **expected** — POST chỉ ghi outbox, broker publish ở background. Xem Step 5 |
| Service `XYZ` không hiện trong dropdown Jaeger | Service đó chưa nhận request nào, hoặc OTel chưa init. Bắn request rồi đợi 5s |

---

## Phần 5 — Cross-reference Grafana ↔ Jaeger

Khi Grafana báo "p99 latency cao", muốn xem trace cụ thể tin nào chậm:

### 5.1 Lọc theo duration trong Jaeger

1. Mở Jaeger, chọn service
2. Set **Min Duration** = 100ms → tìm trace chậm bất thường
3. Click → xem span nào dài → biết bottleneck thật

### 5.2 Filter theo error

Tags: `http.status_code=500` hoặc `error=true` → list mọi request lỗi.

### 5.3 Xem tail latency cụ thể

Thay vì xem percentile chung trên Grafana, click 1 trace dài cụ thể trên Jaeger → biết EXACT vì sao chậm.

### 5.4 Liên hệ với metrics

Cùng 1 service `message-service`:
- **Grafana** = aggregate (1 line = throughput/latency của toàn service)
- **Jaeger** = chi tiết (1 row = 1 request cụ thể)

Workflow điển hình: thấy spike latency trên Grafana → mở Jaeger filter lookback đúng cửa sổ đó → xem trace gây spike.

---

## Phần 6 — Troubleshoot

| Triệu chứng | Hành động |
|---|---|
| Jaeger UI báo "No services" | Container `zalord-jaeger` chưa lên, hoặc `otel-collector` chưa healthy. Check `docker compose ps` |
| Service tồn tại nhưng không có operation | Service chưa nhận request nào — bắn 1 request rồi đợi 5s (batch processor) |
| Trace ngắt giữa chừng, không thấy span service B | Context propagation hỏng. Check service B đã instrument chưa. Hoặc giao thức giữa A↔B không support propagation |
| Kong trace không xuất hiện | Check `KONG_TRACING_SAMPLING_RATE` = "1.0" trong [docker-compose.yml](../../docker-compose.yml). Restart Kong |
| Quá nhiều trace, browser lag | Giảm `KONG_TRACING_SAMPLING_RATE` xuống 0.1 (10%) hoặc set lookback nhỏ hơn |
| Spans thiếu DB query | Driver chưa được agent instrument. Check Java agent version, hoặc Go SDK chưa add database/sql wrapping |
| Span gRPC chỉ thấy client, thiếu server | Bên server chưa init OTel SDK, hoặc port gRPC khác config |
| Restart Jaeger mất hết trace | All-in-one dùng in-memory storage. Production dùng Cassandra/ES backend |

---

## Phần 7 — Mở rộng

### 7.1 Thêm custom span trong code Java

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

@Service
public class MyService {
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("my-service");

    public void doWork() {
        Span span = tracer.spanBuilder("expensiveOperation").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("user.id", userId);
            // ... work ...
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### 7.2 Thêm custom span trong code Go

```go
import "go.opentelemetry.io/otel"

tracer := otel.Tracer("chat-service")

func (h *MyHandler) DoWork(ctx context.Context) {
    ctx, span := tracer.Start(ctx, "expensiveOperation")
    defer span.End()
    
    span.SetAttributes(attribute.String("user.id", userID))
    // ... work ...
}
```

### 7.3 Production hardening

- [ ] Đổi `KONG_TRACING_SAMPLING_RATE` xuống 0.01-0.1 (1-10%) — 100% tracing ở prod sẽ OOM Jaeger
- [ ] Đổi Jaeger backend từ in-memory sang **Cassandra** hoặc **Elasticsearch** (persistent + scale)
- [ ] Hoặc dùng managed: **Grafana Tempo** / **Datadog APM** / **Honeycomb**
- [ ] Tail-based sampling ở collector — chỉ keep trace có error hoặc slow (`> p95`)
- [ ] Restrict access Jaeger UI (port 16686) — không expose public

---

## Phần 8 — Quick reference

### URLs
- **Jaeger UI**: http://localhost:16686
- **OTel collector health**: http://localhost:13133 (curl, trả 200 = OK)
- **OTel metrics from collector**: http://localhost:8889/metrics (Prometheus scrape)

### CLI query Jaeger API
```bash
# List services
curl -s http://localhost:16686/api/services | jq

# Latest trace từ service X
curl -s "http://localhost:16686/api/traces?service=auth-service&limit=1" | jq

# Trace cụ thể
curl -s "http://localhost:16686/api/traces/<traceID>" | jq

# Tìm trace slow > 100ms
curl -s "http://localhost:16686/api/traces?service=message-service&minDuration=100ms&limit=10" | jq
```

### Logs collector
```bash
docker logs zalord-otel-collector --tail 50
docker logs zalord-jaeger --tail 50
```

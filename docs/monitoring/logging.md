# Hướng dẫn dùng Loki — Centralized Logging

> Doc này viết cho người **chưa quen** Loki/LogQL. Tất cả thao tác trên **Grafana UI** (không cần CLI). Đọc theo thứ tự từ đầu.

---

## Phần 1 — Loki là gì? Khác gì với `docker logs`?

Bình thường muốn xem log 1 service:
```bash
docker logs zalord-message-service --tail 50
```
→ Chỉ xem được 1 container 1 lần. Muốn tìm "user X gặp lỗi ở service nào" phải grep từng container. Không có full-text search, không time range, không link với trace.

**Loki là log aggregation** — thu thập log của **mọi container** về 1 chỗ, cho query trên Grafana Explore. Tương tự Prometheus cho metrics, Jaeger cho traces:

| | Prometheus | Loki | Jaeger |
|---|---|---|---|
| Loại | Metrics (số) | Logs (text) | Traces (span) |
| Query language | PromQL | **LogQL** | (form filter) |
| UI | Grafana dashboard | Grafana Explore | Jaeger UI |

Loki thiết kế theo triết lý "**index labels, not content**" — chỉ index metadata (service name, container name, log level), còn content được compress + search khi query. Rẻ hơn Elasticsearch nhiều.

## Phần 2 — Kiến trúc trong Zalord

```
┌─ mọi container ──────────────────────────────────────────┐
│  auth-service     stdout/stderr  ─┐                       │
│  message-service  stdout/stderr  ─┤                       │
│  chat-service     stdout/stderr  ─┤                       │
│  ... 28 container ...             │                       │
└───────────────────────────────────┼───────────────────────┘
                                    │
                                    ▼ Docker log driver
                           ┌──────────────────┐
                           │ zalord-promtail  │  ← agent scrape logs
                           │ (Grafana agent)  │     qua /var/run/docker.sock
                           └────────┬─────────┘
                                    │ HTTP push (batched)
                                    ▼
                           ┌──────────────────┐
                           │ zalord-loki      │  ← storage + query engine
                           │ (port 3100)      │     retention 7 ngày
                           └────────┬─────────┘
                                    │ HTTP API
                                    ▼
                           ┌──────────────────┐
                           │ Grafana Explore  │  ← UI query
                           │ (port 3000)      │
                           └──────────────────┘
```

**Files liên quan**:
- [infra/loki/loki-config.yaml](../../infra/loki/loki-config.yaml) — Loki storage + retention
- [infra/promtail/promtail-config.yaml](../../infra/promtail/promtail-config.yaml) — agent scrape rules
- [infra/grafana/provisioning/datasources/loki.yaml](../../infra/grafana/provisioning/datasources/loki.yaml) — datasource + link Jaeger
- [docker-compose.yml](../../docker-compose.yml) — `loki` + `promtail` container definition

---

## Phần 3 — Setup lần đầu

```bash
make dev
# Đợi container up
docker compose ps loki promtail
# → cả 2 phải "Up"

# Verify Loki nhận log
curl -s "http://localhost:3100/loki/api/v1/label/service/values" | jq '.data | length'
# → in ra số ≥ 20 (tùy stack)
```

---

## Phần 4 — Mở Grafana Explore

### 4.1 Vào UI

URL: **http://localhost:3000**  
Login: `admin` / `admin` (hoặc bấm Skip)

### 4.2 Tìm nút Explore

Sidebar bên trái (icon la bàn 🧭):

```
┌────────────────┐
│ 🏠 Home        │
│ 🔖 Bookmarks   │
│ ⭐ Starred     │
│ 📊 Dashboards  │  ← metrics dashboard nằm ở đây
│ 🧭 Explore     │  ← ← LOGS/TRACES QUERY nằm ở đây
│ 🔔 Alerting    │
│ 🔌 Connections │
└────────────────┘
```

Click **Explore**.

### 4.3 Đổi datasource sang Loki

Trên cùng của Explore page có dropdown datasource. Mặc định là "Prometheus" (metrics). Click vào dropdown → chọn **Loki**:

```
┌─────────────────────────────────┐
│ Outline | [ Prometheus ▼ ]     │
├─────────────────────────────────┤
│    ├─ Prometheus (metrics)     │
│    ├─ Loki  ← chọn cái này     │
│    └─ Jaeger (traces)          │
└─────────────────────────────────┘
```

---

## Phần 5 — Query log đầu tiên

### 5.1 Dùng UI Label Filter (dễ nhất)

Sau khi chọn Loki, sẽ thấy form:

```
┌──────────────────────────────────────────────────────┐
│ Label filters                                        │
│ ┌───────────┐  ┌──────────────────┐   [+ Add]        │
│ │ service ▼ │= │ message-service ▼│                  │
│ └───────────┘  └──────────────────┘                  │
│                                                      │
│ Line contains  ┌────────────────────┐                │
│                │ (regex or text)    │                │
│                └────────────────────┘                │
│                                                      │
│                              [ Run query ]           │
└──────────────────────────────────────────────────────┘
```

Bước:
1. Click ô Label → chọn `service`
2. Click ô Value → chọn `message-service` (hoặc bất kỳ)
3. Bấm **Run query** (hoặc **Shift+Enter**)

### 5.2 Kết quả — 2 phần

**Phần trên: Histogram** — biểu đồ số log/phút:

```
      log count
   30 │      ▄▄
   20 │  ▄▄▄▄██▄▄
   10 │▄▄████████▄▄
    0 └───────────────► time
```

**Phần dưới: Log lines** — bảng log realtime:

```
Timestamp            Labels                          Log line
────────────────────────────────────────────────────────────────
04:48:18.403  service=message-service INFO...  2026-07-01T04:48:18.403Z INFO ...
04:48:18.402  service=message-service INFO...  2026-07-01T04:48:18.402Z INFO ...
...
```

Click 1 dòng → mở detail panel bên phải với đầy đủ label + line breakdown.

---

## Phần 6 — LogQL — query language

LogQL cú pháp giống PromQL. 2 phần: **stream selector** (chọn log stream bằng label) + **filter** (lọc theo content).

### 6.1 Stream selector — bắt buộc

```logql
{service="message-service"}
```
= mọi log có label `service=message-service`.

Có thể combine nhiều label:
```logql
{service="message-service", container="zalord-message"}
```

Regex value bằng `=~`:
```logql
{service=~"message-service|chat-service"}
```

Not-equal `!=`:
```logql
{service!="loki"}
```

### 6.2 Content filter — optional

Sau `{...}` thêm filter:

| Operator | Nghĩa | Ví dụ |
|---|---|---|
| `\|=` | Contains string | `{service="kong"} \|= "429"` |
| `!=` | Not contains | `{service="message-service"} != "actuator"` |
| `\|~` | Regex match | `{service="message-service"} \|~ "user=[a-f0-9-]{36}"` |
| `!~` | Not regex | `{service=~".*"} !~ "health\|prometheus"` |

Chain nhiều filter:
```logql
{service="message-service"} |= "ERROR" != "GET /health"
```
= log có "ERROR" nhưng không phải health check.

### 6.3 Parse JSON / logfmt

Nếu app log kiểu JSON, dùng `| json`:
```logql
{service="message-service"} | json
```
→ Grafana parse các field JSON thành label, hiển thị đẹp.

Sau parse, filter theo field:
```logql
{service="message-service"} | json | level="ERROR"
```

### 6.4 Aggregate — rate, count

Biến logs thành **metric**:

| Query | Nghĩa |
|---|---|
| `rate({service="message-service"}[1m])` | Số log/giây gần 1 phút |
| `count_over_time({service="message-service"} \|= "ERROR" [5m])` | Số error trong 5 phút |
| `sum by (service) (rate({compose_project="zalord"}[1m]))` | Log rate/service (dashboard) |

Cần Range vector (`[1m]`, `[5m]`) như PromQL.

---

## Phần 7 — Demo scenarios cho defense

### 7.1 Scenario 1 — "user X gặp lỗi ở đâu?"

Query mọi log có UUID user:
```logql
{compose_project="zalord"} |= "d8b7aa69-fac1-4fa6-82c3-f38351b502ea"
```

→ Grafana show log line ở mọi service liên quan (auth khi login, message khi gửi tin, chat khi push WS...). Nhìn 1 phát biết flow user chạy qua đâu.

### 7.2 Scenario 2 — "gần đây có error nào?"

```logql
{compose_project="zalord"} |~ "(?i)error|exception|fatal"
```

`(?i)` = case-insensitive. Match cả "ERROR", "error", "Error".

→ Histogram phía trên hiện spike khi có nhiều lỗi. Zoom vào để tìm chính xác lúc bùng nổ.

### 7.3 Scenario 3 — "so sánh log giữa 2 service"

Split view: bấm nút **+ Split** góc trên phải:

```
┌──────────────────┬──────────────────┐
│ Panel A          │ Panel B          │
│ {service=      } │ {service=      } │
│ message-service  │ chat-service     │
│                  │                  │
│ log stream ...   │ log stream ...   │
└──────────────────┴──────────────────┘
```

→ 2 panel song song, cùng time range, so log 2 service trong cùng 1 khoảnh khắc.

### 7.4 Scenario 4 — Follow log realtime (tail -f style)

Bấm nút **Live** góc trên phải khi đang query → log stream chảy vào realtime, giống `tail -f`.

Dừng bằng nút Stop.

### 7.5 Scenario 5 — Query khi benchmark chạy

Trong lúc `scripts/run-benchmark-rabbit.sh` chạy:
```logql
{service="message-service"} |= "Message sent"
```
→ Sẽ thấy log rate tăng cao (200-500 tin/s), sau đó tụt về 0 khi benchmark xong.

Combine với query metric:
```logql
sum(rate({service="message-service"} |= "Message sent" [1m]))
```
→ Biểu đồ throughput từ log-based counter — chéo check với Prometheus metric.

---

## Phần 8 — Correlate log với trace (Jaeger)

Đây là **magic** của observability tích hợp.

### 8.1 Điều kiện

Log line phải có `trace_id=<hex>` trong content. Java OTel agent tự inject nếu Logback được config MDC:
```
INFO [message-service] [trace_id=abc123..., span_id=def456] msg="..."
```

Hiện Zalord **chưa wire MDC** — sẽ setup ở turn sau. Nhưng framework đã sẵn sàng.

### 8.2 Cách dùng

Trong Grafana Explore Loki, click 1 log line có trace_id:

```
┌────────────────────────────────────────────────────┐
│ [message-service] INFO trace_id=abc123... ...      │
│ ┌────────────────────────────────────────────────┐ │
│ │ Detected fields:                                │ │
│ │  trace_id: abc123def456...                      │ │
│ │  ├─ [🔗 Jaeger] ← click nhảy sang Jaeger UI    │ │
│ │                                                 │ │
│ │ Labels:                                         │ │
│ │  service: message-service                       │ │
│ └────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────┘
```

Click **🔗 Jaeger** → mở trace tương ứng ở Jaeger UI, thấy waterfall span.

### 8.3 Config đã sẵn — chỉ cần app log có trace_id

Trong [datasources/loki.yaml](../../infra/grafana/provisioning/datasources/loki.yaml) có `derivedFields`:
```yaml
derivedFields:
  - name: TraceID
    matcherRegex: 'trace_id[":\s=]+([a-f0-9]{32})'
    url: '${__value.raw}'
    datasourceUid: jaeger
```

Regex tìm `trace_id=<32 hex>` trong log line → tạo link tới Jaeger với trace_id đó.

→ Khi app log có `trace_id=abc...`, Grafana tự animate hiện nút link.

---

## Phần 9 — Troubleshoot

| Vấn đề | Fix |
|---|---|
| Explore không thấy datasource Loki | Container `zalord-loki` chưa lên → `docker compose ps loki` |
| "No logs found" | (1) Set lookback rộng hơn (Last 1 hour). (2) Bỏ label filter, chỉ dùng `{compose_project="zalord"}`. (3) Chắc chắn container còn output log (`docker logs <name>`) |
| Query chậm | Loki index label không index content — content filter (`\|=`) phải scan toàn bộ log trong time range. Giảm time range nhỏ hơn |
| Trace_id không hiện thành link | Log line chưa có `trace_id=<hex>` — MDC chưa wire. Xem doc riêng về tracing MDC (TODO) |
| Log tiếng Việt/emoji không hiện đúng | Byte-order mark (BOM) hoặc encoding — Loki hiện text UTF-8, check output container `docker logs` xem đúng encoding chưa |
| Container mới không thấy trong dropdown Value | Container chưa emit log line nào từ khi Promtail start. Bắn traffic 1 request qua service đó rồi refresh |
| "429 too many requests" từ Loki | Vượt `ingestion_rate_mb` (8 MB/s config) — hiếm ở dev. Tăng lên trong [loki-config.yaml](../../infra/loki/loki-config.yaml) |

---

## Phần 10 — Retention và cost

Zalord config (dev):
- Retention: **7 ngày** (168h) — log cũ hơn tự xóa
- Storage: filesystem trong container `zalord-loki` volume `loki_data`
- Ingest limit: 8 MB/s per tenant
- Query max window: 30 ngày

Production khác:
- Storage: **S3/GCS** thay cho filesystem
- Retention: 30 ngày hot + archive S3 lifetime
- Multi-tenancy: bật `auth_enabled: true`
- HA: Loki Distributed mode (multiple ingester + querier)

Trong scope Zalord (course + 8GB VPS), dev config đủ dùng. Production checklist xem [Grafana Loki docs](https://grafana.com/docs/loki/latest/setup/deploy/).

---

## Phần 11 — Quick reference

### URLs
- **Grafana**: http://localhost:3000 → Explore → chọn Loki datasource
- **Loki API**: http://localhost:3100
- **Loki metrics** (Prometheus scrape): http://localhost:3100/metrics

### CLI query
```bash
# Labels hiện có
curl -s http://localhost:3100/loki/api/v1/labels | jq

# Value của label "service"
curl -s http://localhost:3100/loki/api/v1/label/service/values | jq

# Query recent — 5 phút gần nhất
QUERY=$(printf '{service="message-service"}' | jq -sRr @uri)
curl -s "http://localhost:3100/loki/api/v1/query_range?query=${QUERY}&limit=10" | jq
```

### LogQL cheatsheet
```logql
# Chọn service
{service="message-service"}

# Regex nhiều service
{service=~"message-service|chat-service|kong"}

# Content filter
{service="message-service"} |= "ERROR"
{service="kong"} |~ "status=(4|5)\\d\\d"

# Parse JSON
{service="chat-service"} | json | level="error"

# Aggregate
sum by (service) (rate({compose_project="zalord"}[1m]))
count_over_time({service="message-service"} |= "ERROR" [5m])
```

### Doc song hành
- [observability.md](./observability.md) — Metrics (Prometheus + Grafana)
- [tracing.md](./tracing.md) — Traces (OTel + Jaeger)
- [find-trace-ui.md](./find-trace-ui.md) — UI workflow tìm trace
- [benchmark.md](./benchmark.md) — Load test

# Hướng dẫn dùng Jaeger UI để trace 1 request

> **Mục tiêu**: Bạn vừa gửi 1 HTTP request (qua browser, Postman, frontend, hay bất cứ đâu) → giờ muốn xem trong Jaeger UI request đó đã đi qua những service nào, mỗi service tốn bao lâu. **Toàn bộ thao tác trên UI, không cần CLI.**

---

## Bước 0 — Chuẩn bị (1 lần)

1. **Stack phải up**: `make dev` (đợi container healthy)
2. **Jaeger UI URL**: http://localhost:16686
3. Mở URL trên trong browser → thấy logo Jaeger + search form

Không cần đăng nhập, không cần config gì.

---

## Cách 1 — Tìm trace của 1 request VỪA gửi (UI thuần)

Use case phổ biến nhất: bạn vừa làm 1 action trên frontend hoặc curl, giờ muốn xem trace.

### Bước 1: Gửi request

Bạn cứ gửi request bình thường — qua browser frontend, Postman, hoặc lệnh curl bất kỳ:

```
POST http://localhost:8080/api/v1/messages
GET  http://localhost:8080/api/v1/users/me
PUT  http://localhost:8080/api/v1/conversations/abc/members
```

**Lưu ý thời gian** bạn bấm gửi (vd 14:32:05).

### Bước 2: Mở Jaeger UI

`http://localhost:16686`

Bạn thấy giao diện như sau:

```
┌────────────────────────────────────────────────────────────────┐
│ [logo] Jaeger    [Search] [Compare] [System Arch] [Monitor]    │  ← tabs
├────────────────────────────────────────────────────────────────┤
│                                                                │
│   Service:    [ ▼ chọn service ]                               │
│   Operation:  [ ▼ all  ]                                       │
│   Tags:       [                                              ] │
│   Lookback:   [ ▼ Last Hour ]                                  │
│   Min Duration: [        ]   Max Duration: [        ]          │
│   Limit Results: [ 20 ]                                        │
│                                                                │
│                       [ Find Traces ]                          │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Bước 3: Chọn service

Click dropdown **Service** → chọn service mà request của bạn **đi tới**.

Quy tắc: chọn service ở **endpoint cuối**, không phải Kong. Ví dụ:

| URL bạn gửi | Service chọn |
|---|---|
| `POST /api/v1/auth/login` | `auth-service` |
| `GET /api/v1/users/me` | `user-service` |
| `POST /api/v1/messages` | `message-service` |
| `POST /api/v1/conversations` | `message-service` |
| `GET /api/v1/groups` | `group-service` |
| `POST /api/v1/media/upload` | `media-service` |
| WebSocket `/ws/chat` | `chat-service` |

(Nếu không chắc, chọn `kong-gateway` — sẽ thấy mọi request đi qua Kong)

### Bước 4: (Optional) Chọn operation

Sau khi chọn service, dropdown **Operation** sẽ tự load. Click vào dropdown → thấy list các endpoint của service đó:

```
[ all ▼ ]
  ├─ POST /api/v1/messages
  ├─ GET  /api/v1/messages/{id}
  ├─ POST /api/v1/conversations
  ├─ GET  /api/v1/inbox
  └─ ...
```

Chọn operation match URL của bạn (vd `POST /api/v1/messages`).

→ Filter giúp loại bớt trace không liên quan.

### Bước 5: Set lookback

Click **Lookback** → chọn "Last 5 minutes" (hoặc nhỏ hơn) nếu bạn vừa bấm gửi xong.

(Mặc định "Last Hour" — quá rộng, sẽ thấy nhiều trace cũ không liên quan)

### Bước 6: Bấm "Find Traces"

Đợi ~2 giây. Kết quả hiện như sau:

```
┌────────────────────────────────────────────────────────────────┐
│  20 Traces found                       Sort: [Most Recent ▼]   │
├────────────────────────────────────────────────────────────────┤
│ ▼ message-service: POST /api/v1/messages           15.2ms       │
│   ├ 23 spans, 2 services (kong-gateway, message-service)        │
│   ├ Tags: http.status_code=201, http.method=POST                │
│   └ 14:32:06 UTC, 2 minutes ago                                 │
├────────────────────────────────────────────────────────────────┤
│ ▼ message-service: POST /api/v1/messages           18.5ms       │
│   ├ 21 spans, 2 services                                        │
│   └ 14:30:14 UTC, 4 minutes ago                                 │
├────────────────────────────────────────────────────────────────┤
│ ...                                                             │
└────────────────────────────────────────────────────────────────┘
```

Mỗi row là 1 trace. Hiển thị:
- **Tên**: `<service>: <operation>`
- **Thời lượng**: tổng end-to-end (15.2ms)
- **Số span**: 23 span (tức 23 đoạn việc nhỏ)
- **Services**: list service tham gia
- **Thời điểm**: bao lâu trước

### Bước 7: Tìm đúng trace của bạn

Cách phân biệt trace của bạn vs trace người khác cùng lúc:

1. **Sort by Most Recent** (mặc định) → trace ở top là mới nhất → khả năng cao là của bạn
2. So thời gian: nếu bạn bấm gửi lúc 14:32:05, tìm row có timestamp khoảng đó
3. Nếu trùng nhiều quá: dùng **Tags** filter (xem Cách 2 bên dưới)

### Bước 8: Click trace → xem waterfall

Click vào tên trace → màn hình mở:

```
┌─────────────────────────────────────────────────────────────────┐
│ POST /api/v1/messages                                           │
│ Trace ID: 37cfa4ed2cfa28358f3d34ee0725425e                      │
│ Duration: 15.2ms     Services: 2     Depth: 4     Total Spans: 23│
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   0ms                                                    15ms   │
│   │                                                       │     │
│   ├ kong [kong-gateway]                            ███████████   │
│   │  ├ kong.access.plugin.cors                     ▏             │
│   │  ├ kong.access.plugin.jwt                      ▎             │
│   │  ├ kong.access.plugin.rate-limiting            ▎             │
│   │  ├ kong.access.plugin.opentelemetry            ▏             │
│   │  ├ kong.dns                                    ████          │
│   │  ├ kong.balancer                               ▌             │
│   │  └ POST /api/v1/messages [message-service]     ██████████    │
│   │     ├ SELECT conversation_members              ▎             │
│   │     ├ INSERT messages                          ▎             │
│   │     ├ INSERT outbox_events                     ▏             │
│   │     └ Transaction.commit                       █             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Cách đọc waterfall**:

- **Mỗi dòng** = 1 span (1 đoạn việc)
- **Chữ trong []** = service đang xử lý span đó
- **Indent (thụt vào)** = parent-child (span trong indent là con của span trên không indent)
- **Bar dài** = span tốn nhiều thời gian
- **Vị trí bar** = lúc nào span start trong tổng thời gian
- **Bar ngắn xíu (▏)** = < 1ms
- **Màu sắc** = phân biệt service (Kong xanh, message-service vàng...)

→ **Đọc xuống thấy ngay** request đã đi qua những service nào, tốn bao lâu ở mỗi chỗ.

### Bước 9: Click span để xem chi tiết

Click vào 1 span (vd `INSERT messages`) → panel phải hiện:

```
┌──────────────────────────────────────────┐
│ INSERT messages                          │
│ Duration: 0.262ms                        │
│ Start time: 12.8ms                       │
├──────────────────────────────────────────┤
│ Tags                                     │
│  ├ db.system: postgresql                 │
│  ├ db.name: message-db                   │
│  ├ db.statement: insert into messages... │
│  ├ db.operation: INSERT                  │
│  └ db.sql.table: messages                │
├──────────────────────────────────────────┤
│ Process                                  │
│  ├ service.name: message-service         │
│  ├ host.name: zalord-message             │
│  └ container.id: abc123...               │
└──────────────────────────────────────────┘
```

→ Thấy **chính xác query SQL nào**, **vào DB nào**, **tốn bao lâu**.

---

## Cách 2 — Tìm trace theo URL chính xác (dùng Tags)

Khi cùng lúc có 20 request trùng endpoint, dùng tag để filter chính xác.

### Bước 1+2+3+5+6: Như Cách 1

### Bước 4 (Tags): nhập filter

Trong ô **Tags**, gõ:

```
http.target=/api/v1/messages
```

(Có thể nhiều tag, cách bằng space)

Các tag hữu ích:

| Tag | Ví dụ giá trị | Lọc gì |
|---|---|---|
| `http.target` | `/api/v1/messages` | URL path |
| `http.method` | `POST` | HTTP verb |
| `http.status_code` | `500` | Chỉ trace lỗi |
| `http.status_code` | `201` | Chỉ trace thành công |
| `http.url` | `http://localhost:8083/api/v1/messages` | Full URL |
| `error` | `true` | Chỉ trace có error |
| `user.id` | `d8b7aa69-...` | (nếu code add custom tag) |

Có thể combine:
```
http.target=/api/v1/messages http.status_code=500
```
→ Chỉ thấy POST /messages lỗi 500.

### Bước 7+8+9: Như Cách 1

---

## Cách 3 — Tìm trace chậm bất thường (Min Duration)

Khi user complain "tin nhắn gửi chậm", muốn tìm chính xác trace nào tốn >100ms.

### Bước 1-5: Như Cách 1

### Bước 6: Set Min Duration

Trong ô **Min Duration**, gõ:
```
100ms
```

(Có thể `1s`, `500ms`, `10ms`)

### Bước 7: Bấm Find Traces

Chỉ trace tốn ≥100ms hiện ra. Click vào trace dài nhất → xem span nào tốn nhiều thời gian = bottleneck.

---

## Cách 4 — Tìm trace có lỗi

Tag **`error=true`** hoặc **`http.status_code=500`**:

```
Tags: error=true
```

Hoặc cụ thể status:
```
Tags: http.status_code=500
```

→ Chỉ trace có lỗi. Click vào → span màu **đỏ** = chỗ thật sự error.

---

## Cách 5 — Xem service nào hay gọi service nào (System Architecture)

Click tab **System Architecture** (trên thanh menu).

Sẽ thấy đồ thị service-to-service:

```
   ┌─────────────┐
   │ kong-gateway│
   └──┬───┬───┬──┘
      │   │   │
      ▼   ▼   ▼
   ┌────┐┌─────┐┌──────┐
   │auth││user ││message│
   └────┘└─────┘└──┬───┘
                   │
                   ▼ gRPC
                ┌──────┐
                │ media│
                └──────┘
```

Mỗi node = 1 service. Mũi tên = call direction. Click vào edge → xem latency trung bình + số call.

→ Hữu ích để hiểu **topology** hệ thống mà không cần đọc code.

---

## Demo thực tế (live)

### Setup
1. `make dev` (đợi tất cả container healthy)
2. Register Alice nếu chưa có:
   ```
   curl -X POST http://localhost:8080/api/v1/auth/register \
     -H 'Content-Type: application/json' \
     -d '{"phoneNumber":"0900111001","password":"secret123","displayName":"Alice"}'
   ```

### Demo
1. Mở 2 browser tab:
   - Tab A: http://localhost:16686 (Jaeger)
   - Tab B: bất cứ tool gửi request (Postman, hoặc terminal curl)

2. Trong Tab B, gửi 1 request:
   ```
   POST http://localhost:8080/api/v1/auth/login
   Body: {"phoneNumber":"0900111001","password":"secret123"}
   ```

3. Sang Tab A (Jaeger):
   - Service: chọn **`auth-service`**
   - Operation: chọn **`POST /api/v1/auth/login`**
   - Lookback: **Last 5 minutes**
   - Bấm **Find Traces**

4. Thấy trace top → click vào → waterfall view:
   ```
   kong (15ms)
   ├ kong.access.plugin.cors
   ├ kong.access.plugin.rate-limiting
   ├ kong.dns
   ├ kong.balancer
   └ POST /api/v1/auth/login (auth-service, 8ms)
      ├ SELECT users WHERE phone_number=?
      ├ PasswordEncoder.matches      ← bcrypt, hay tốn nhất
      └ JWT.sign
   ```

5. Click span `PasswordEncoder.matches` → panel phải hiện tags (`crypto.algorithm: bcrypt`, ...).

→ Bạn vừa trace 1 request **mà không cần viết 1 dòng code nào**.

---

## Troubleshoot UI

| Vấn đề | Fix |
|---|---|
| Mở `localhost:16686` không vào được | Stack chưa up — `docker compose ps zalord-jaeger` |
| Dropdown Service trống | Chưa có service nào gửi trace — bắn 1 request rồi đợi 5s |
| Bấm Find Traces không ra gì | (1) Set lookback rộng hơn ("Last Hour"). (2) Bỏ filter tag/operation. (3) Đợi thêm 5s rồi thử lại (batch delay) |
| Thấy quá nhiều trace, không pick được | Nhập **http.target=/url/cua/ban** vào Tags để lọc theo URL |
| Trace hiện nhưng span ít, chỉ 1-2 | Service chưa instrument OTel — chưa work cho 1 số custom endpoint |
| Waterfall view bị tràn ngoài, không thấy hết | Bấm vào span chính → "Focus" để zoom; hoặc dùng minimap góc trên |

---

## Tóm tắt 5 cách tìm trace trong UI

| Cách | Khi nào dùng |
|---|---|
| **1. Service + Operation + Lookback** | Tìm trace vừa gửi, biết service đích |
| **2. + Tags `http.target=...`** | Lọc chính xác theo URL khi có nhiều trace trùng |
| **3. + Min Duration** | Tìm trace chậm bất thường |
| **4. + Tags `error=true`** | Tìm trace lỗi |
| **5. System Architecture tab** | Xem topology service-to-service |

---

## URLs cần biết

- **Jaeger UI**: http://localhost:16686
- **Grafana** (metrics, không phải trace): http://localhost:3000
- **Prometheus** (raw metrics): http://localhost:9090

Doc song hành:
- [tracing.md](./tracing.md) — sâu hơn về concept, code snippet, propagation
- [observability.md](./observability.md) — metrics + dashboard

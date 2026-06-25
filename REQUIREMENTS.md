# Đánh giá yêu cầu đồ án — Zalord

> Tài liệu này soi 16 yêu cầu của đồ án vào hệ thống Zalord hiện tại và phân loại:
> - ✅ **Đã làm** — bằng chứng + file đối chiếu.
> - ⚠️ **Nên làm thêm** — phù hợp với scope, đáng làm; có ước lượng công sức.
> - ❌ **Không phù hợp** — giải thích vì sao bỏ qua (KHÔNG phải vì lười).
>
> Cuối tài liệu có bảng tổng hợp + lộ trình đề xuất.

---

## 1. Vì sao **monolith không phù hợp** với hệ này — ✅ đã có bằng chứng

Bốn lý do **bắt nguồn từ workload thực tế**, không phải "tại microservice trendy":

### 1.1 Workload không đồng nhất → scaling profile khác nhau
| Service | Bottleneck dự kiến | Tài nguyên cần scale |
|---|---|---|
| chat-service | Số WS connection đồng thời | RAM + file descriptors (mục tiêu 10K conn) |
| message-service | Write throughput + outbox poll | CPU + Postgres connection pool |
| media-service | I/O object storage (MinIO) | Bandwidth + memory cho presign |
| auth-service | CPU (bcrypt + JWT sign) | CPU cores |

Nếu monolith: phải scale **toàn bộ** instance khi chỉ 1 trục cần. Phí 80% RAM vô ích. Microservice cho phép scale chat horizontally (cần) trong khi auth single-instance (đủ).

### 1.2 Polyglot bắt buộc theo ngữ cảnh kỹ thuật
- **Go cho chat-service** — goroutine + channel hợp với hàng nghìn long-lived WS connection. JVM thread-per-connection sẽ chết ở 5K conn vì stack memory.
- **Java cho message/group/auth/media** — type system + Spring Data JPA giúp business rule + transaction phức tạp viết an toàn (outbox, CQRS projection).
- Monolith ép chọn **một runtime duy nhất** → mất 1 trong 2 ưu thế.

### 1.3 Failure isolation
Crash của `notification-service` (bell icon) **không được** kéo theo chat. Monolith: 1 NullPointerException trong bell logic → cả app sập. Microservice: bell sập, chat vẫn chạy.

### 1.4 Triển khai song song nhiều dev
Đồ án 3 người: A làm auth+user, B làm message+chat, C làm media+notification. Mỗi người deploy độc lập, không block nhau. Monolith: rebase merge conflict mỗi PR.

**Bằng chứng trong repo**:
- `docs/README.md` ghi rõ "Stage 1 monolith đã được abandoned"
- `backend-deprecated/` là di tích của monolith bị bỏ
- Hiện tại 7 service riêng, mỗi service container + DB riêng

---

## 2. Mỗi service = 1 **bounded context** (DDD) — ✅ đã thoả mãn

| Service | Bounded Context | Aggregate root | Owned data |
|---|---|---|---|
| auth-service | **Identity & Credentials** | User (auth perspective) | users, roles, credentials |
| user-service | **Profile** | Profile | profiles (display name, avatar ref, phone) |
| message-service | **Conversation** (gồm Message như entity con) | Conversation | conversations, conversation_members, messages, message_attachments, conversation_views (read model), outbox_events |
| group-service | **Group lifecycle** | Group | groups, group_members |
| media-service | **Media asset** | Media | media (metadata), MinIO objects (bytes) |
| chat-service | **Realtime delivery session** | WS Session | (in-memory + Redis presence) |
| notification-service | **Notification feed** | Notification | notifications per-user |

**Tại sao split như vậy**:

- **Identity vs Profile (auth-service vs user-service) tách ra** vì 2 BC khác biệt:
  - Identity = "chứng minh anh là ai" (credentials, JWT)
  - Profile = "anh trông như thế nào với người khác" (displayName, phone, avatar)
  - Một user có thể đổi displayName 10 lần mà identity không đổi; ngược lại có thể đổi password mà profile không đổi → 2 lifecycle khác → 2 BC.

- **Conversation vs Group tách ra** vì:
  - Group là khái niệm xã hội (admin, member roles, group name)
  - Conversation là khái niệm message routing (DIRECT hoặc GROUP đều thành conversation)
  - GROUP → group-service publish event `group.member.added` → message-service project thành `conversation_members` row. **Group không biết về tin nhắn**, message không biết về role admin.

- **Realtime delivery (chat-service) tách khỏi persistence (message-service)** vì:
  - chat-service stateful (WS connection, presence) — restart = mất hết session, không sao
  - message-service stateless + Postgres — restart không mất data
  - Khác failure mode → tách instance khác nhau

**Không có cross-service FK trong DB**. `user_id` ở message-service chỉ là UUID, không phải FK trỏ về `auth.users` — đó là dấu hiệu BC độc lập đúng nghĩa.

---

## 3. **SRP + High Cohesion / Low Coupling** — ✅ phần lớn, ⚠️ message-service hơi nặng

### Đánh giá per-service

| Service | SRP | Cohesion | Coupling | Ghi chú |
|---|---|---|---|---|
| auth | ✅ chỉ làm auth | Cao | Thấp (chỉ gRPC tới user khi register) | "Sạch" nhất |
| user | ✅ chỉ profile | Cao | Thấp (1 gRPC server, 3 REST endpoint) | |
| message | ⚠️ message + conversation + outbox + inbox view projector | Trung bình | Trung bình | **Lớn nhất** — chia tiếp được? Xem note dưới |
| group | ✅ group lifecycle | Cao | Thấp | |
| chat | ✅ chỉ WS delivery + realtime signal (typing/presence/seen/recall) | Cao | Thấp | |
| media | ✅ presign URL + validate | Cao | Thấp | |
| notification | ✅ chỉ bell feed | Cao | Thấp | |

### Note — message-service nặng nhưng có chủ ý

message-service đang gánh 4 thứ:
1. Conversation/message CRUD (write side)
2. Outbox scheduler (publish events)
3. InboxProjector (build CQRS read model `conversation_views`)
4. GroupEventConsumer (project group.* thành conversation rows)

**Có nên tách CQRS read model thành service riêng "inbox-service"?**
- Pros: SRP "sạch" hơn, scale read độc lập
- Cons: Cần thêm container + DB → tốn RAM (đồ án giới hạn 8GB VPS). Quan trọng hơn: **read model phải đọc trên cùng cluster Postgres** với write model để giảm replication lag, mà mỗi service "phải có DB riêng" → vi phạm nguyên tắc.
- **Quyết định**: giữ chung. Trong nội bộ service, dùng package isolation (`controller/`, `worker/`, `cache/`) để cohesion vẫn cao.

### Execution pipeline cross-service

Lấy ví dụ flow gửi tin nhắn (đo cohesion/coupling qua số service phải sửa khi thay đổi):

```
Client → POST /messages (Kong) → message-service
                                  ↓ gRPC ValidateAttachments
                                  → media-service [chỉ đọc, không sửa state]
                                  ↓ INSERT messages + outbox (1 tx)
                                  → Postgres
OutboxScheduler → publish message.created → EventBus
                                              ↓ fan-out
              ┌─ chat-service (WS push) ──────┤
              ├─ notification-service (bell) ─┤
              └─ message-service InboxProjector (CQRS read model)
```

**4 service tham gia 1 flow** nhưng chỉ liên lạc qua **interface contract** (gRPC proto + event JSON schema). Đổi internal logic của notification không ảnh hưởng 3 service kia → **coupling thấp đạt yêu cầu**.

---

## 4. **Giao tiếp giữa service** — ✅ đã có 3 tier rõ ràng

Đã document đầy đủ trong `DOCUMENT.md` §1 và `docs/realtime-features.md` §2.

| Tier | Use case | Bằng chứng |
|---|---|---|
| **Sync gRPC** | Strong consistency, low volume, caller cần kết quả | auth→user (CreateProfile), message→media (ValidateAttachments) |
| **Pluggable EventBus (RabbitMQ ↔ Kafka)** | High write volume, fan-out | `message.created`, `message.read`, `message.recalled` |
| **Direct RabbitMQ** | Async + 1-N consumer + không cần benchmark | `group.*` events |

### Tại sao **mix sync + async**?

- **Register** sync vì user mong đợi profile sẵn sàng ngay khi `/register` trả 200 (UX). Đi event = phải poll.
- **Validate attachment** sync vì cần reject **trước** khi persist message. Event = bug-prone (race với fan-out).
- **message.created** async vì write rate cao + cần ship cho 3 consumer (chat WS, bell, inbox projector). Sync sẽ block sender chờ tất cả ack.
- **typing** không qua broker luôn — pure WS pipe.

### Tại sao có pluggable EventBus interface cho `message.created`?

Để **benchmark** RabbitMQ vs Kafka cho thesis. Đó là **lý do duy nhất** cho abstraction này — không nhét tất cả event vào interface vì abstraction có cost.

---

## 5. **Endpoint design** — ✅ đã đạt phần lớn, ⚠️ filtering còn yếu

| Yêu cầu | Trạng thái | Bằng chứng |
|---|---|---|
| RESTful naming | ✅ | `/api/v1/{noun}` everywhere; verb là HTTP method |
| Pagination | ✅ | `?page=&size=` trên history, inbox, notifications; `PageResponse<T>` envelope |
| **Filtering** | ⚠️ | Hầu hết endpoint không có filter (vd `?since=` cho history). **Nên thêm nếu tester hỏi** |
| Swagger | ✅ | springdoc-openapi trong mỗi Java service; Go services dùng `swag init`; Kong route `/docs` aggregate (swagger-ui container) |
| Error envelope chuẩn | ✅ | `ApiResponse<T>` (Java) — `{status, message, data, errorCode, timestamp}`; `GlobalExceptionHandler` map exception → status code + errorCode |

### ⚠️ Filtering — gợi ý thêm

Hot endpoint nên có filter:
- `GET /messages?conversationId=...&before=<timestamp>` — load history cũ hơn (cursor pagination)
- `GET /notifications?type=NEW_MESSAGE` — chỉ lấy 1 loại
- `GET /conversations?unreadOnly=true`

Công sức: ~2-3 giờ.

### Inconsistency cần biết

- Go services (chat, notification, user) **không có envelope** `ApiResponse` — trả JSON thẳng. Frontend phải handle 2 shape. **Khuyến nghị docs cho FE rõ** (đã có ở `DOCUMENT.md` §3.3).

---

## 6. **DB per service + Consistency** — ✅ đã đầy đủ

### Strong vs Eventual vs CAP

| Chỗ | Tính chất | Cách đạt |
|---|---|---|
| `messages` + `outbox_events` (1 tx) | **Strong** | Postgres ACID |
| `message_attachments` ← `messages` | **Strong** trong message-service | FK + ON DELETE CASCADE |
| Recall: `recalled_at` + outbox event | **Strong** | Cùng 1 tx |
| `conversation_views` (CQRS read model) | **Eventual** | Build từ event sau ~ms |
| `notifications` (bell) | **Eventual** | Build từ event |
| `presence:online` (Redis) | **AP** (CAP: chấp nhận inconsistency để giữ availability) | Best-effort, TTL heartbeat |
| Typing indicator | **AP** | Pure ephemeral, không persist |
| Media membership cache | **Eventual** | Evict on group.member.removed |

### Saga pattern — ⚠️ chưa làm **đúng saga**, đang là **mini-orchestration**

Register flow là saga thô:
```
auth.register: INSERT user → call user.CreateProfile (gRPC)
                            ├─ thành công: commit
                            └─ thất bại: rollback auth user (compensating)
```

Nhưng hiện tại **chỉ là 2 step**, không có middleware saga (như Axon, Temporal). Nếu cần demo saga đúng nghĩa:
- **Distributed transaction nào nhiều bước hơn?** Hiện tại không có. Tạo group có thể coi như multi-step: group-service tạo group → publish event → message-service tạo conversation + members. Nhưng nếu projection fail thì... group vẫn còn, conversation thiếu → "inconsistency tự sửa": có thể re-publish hoặc reconcile job.

**Khuyến nghị**: trong thesis nêu rõ "chúng tôi dùng Transactional Outbox + idempotent consumer thay vì 2PC/Saga vì các flow phân tán đều có thể eventual, không cần atomic across services". Đây là lập luận chuẩn của microservice.

---

## 7. **Outbox, CQRS, Polyglot Persistence** — ✅ đủ cả ba

### Outbox ✅
- File: `infra/postgres/init-message.sql` bảng `outbox_events`
- Code: `OutboxScheduler.java` poll mỗi 3s, `FOR UPDATE SKIP LOCKED`, publish + set `published_at`
- **Vì sao**: DB write + broker publish KHÔNG thể wrap trong 1 tx (broker không tham gia ACID). Outbox = atomic locally → eventual delivery.

### CQRS ✅
- **Write side**: `messages` (write-optimized, FK, có outbox)
- **Read side**: `conversation_views` (denormalized, indexed cho inbox query 1-shot)
- **Projector**: `InboxProjector` consume `message.created` + `message.recalled`, upsert read model
- **Vì sao**: inbox query phải nhanh (hot path: mở app là load) → denormalize. Write phải atomic → normalize. CQRS = tách 2 bài toán.

### Polyglot Persistence ✅ (theo nghĩa rộng)
| Engine | Use case | Bằng chứng |
|---|---|---|
| Postgres × 6 | Relational data, ACID | Mỗi service 1 DB |
| Redis | Cache + session + pub/sub | `media:{id}`, `conv:{id}:members`, `presence:online`, `presence:events` |
| MinIO (S3) | Object storage (file bytes) | media bucket avatars + attachments |
| RabbitMQ | Topic exchange (default broker) | `*.exchange` |
| Kafka | Alternative broker để benchmark | message.* topics |

**Không có** MongoDB, Elasticsearch, Cassandra, Neo4j. Đề bài cho phép "bỏ qua nếu không có" — system này dùng 3 paradigm (relational + KV + object) là đủ chứng minh khái niệm polyglot.

---

## 8. **Đọc đề cương đầy đủ** — N/A (instruction cho user)

Không phải yêu cầu đối với hệ thống. Skip.

---

## 9. **Service discovery + Load balancing** — ❌ hiện tại chưa cần, ⚠️ k8s đang ở giai đoạn placeholder

### Trạng thái
- **Hiện tại**: Docker Compose. Service discovery = **Docker DNS** (container name = host name). Không cần Eureka/Consul.
- **k8s**: thư mục `deploy/k8s/{base,overlays}/` tồn tại nhưng **rỗng**. Sprint 4 mới làm.
- **Load balancing**: chưa có. Mỗi service single-instance.

### Phù hợp với hệ này không?

**Trong scope Compose (hiện tại)**: KHÔNG cần Eureka/Consul vì Docker DNS đã đủ. Compose tự load-balance round-robin nếu scale (`docker compose up --scale chat-service=3`) nhưng ta chưa dùng.

**Trong scope k8s (tương lai)**:
- ✅ Dùng **k8s Service** cho service discovery (built-in DNS) — không cần Eureka
- ✅ Dùng **k8s Service ClusterIP** cho **server-side LB** (kube-proxy)
- ❌ Client-side LB (Ribbon, gRPC built-in LB) **không phù hợp** vì:
  - Chỉ có ích khi service mesh quản lý connection pool dài hạn
  - Tăng client complexity (mỗi client phải track health của upstream)
  - Server-side LB ở k8s đủ tốt cho 95% case

### Cần làm gì
- **Hiện tại**: KHÔNG cần thêm. Nêu rõ trong thesis: "dùng Compose nên service discovery qua Docker DNS; nếu lên k8s sẽ dùng k8s Service (server-side LB)".
- **Nếu giảng viên yêu cầu k8s manifest**: viết minimal Deployment + Service YAML cho mỗi service. Công sức: ~1 ngày.

---

## 10. **API Gateway features (Kong)** — ⚠️ thiếu rate limit + path rewrite, ❌ ingress không applicable

| Feature | Trạng thái | Ghi chú |
|---|---|---|
| **Filter** | ✅ | Kong plugin model = filter. Đang dùng: `jwt` (verify token), `pre-function` Lua (inject X-User-Id), `cors` |
| **Authentication filter** | ✅ | Plugin `jwt` |
| **Rate limiting** | ❌ chưa có | **Nên thêm** — đơn giản |
| **Path rewrite** | ❌ chưa có | `strip_path: false` everywhere. Hiện tại path FE = path internal → không cần rewrite |
| **Ingress controller** | ❌ N/A | Ingress là khái niệm k8s. Compose không có ingress. **Kong đóng vai trò tương đương** = "edge proxy" |

### Giải thích thuật ngữ

- **Filter (Kong plugin)** = middleware xử lý request trước/sau khi proxy. Vd: JWT plugin verify chữ ký rồi mới forward.
- **Path rewrite** = đổi path khi forward. Vd: client gọi `/api/v1/users/me`, Kong rewrite thành `/me` rồi gửi tới user-service. Hiện tại KHÔNG cần vì service code biết full path.
- **Ingress controller** = component trong k8s nhận traffic external và route tới Service nội bộ (nginx-ingress, Traefik...). Trong Compose, Kong tự làm việc đó.

### Cần làm thêm

**Nên thêm rate limiting** (giảng viên hay hỏi):
```yaml
# infra/kong/kong.yml — thêm plugin global hoặc per-route
- name: rate-limiting
  config:
    minute: 60          # 60 req/phút/IP cho auth endpoint
    policy: local       # in-memory; nếu multi-Kong dùng redis
```
Công sức: 30 phút. Đặc biệt nên có trên `/auth/login` để chống brute force.

---

## 11. **JWT, OAuth2, Keycloak** — ✅ JWT ok, ❌ OAuth2/Keycloak không cần

### Trạng thái
- **JWT**: ✅ HS256, claim `sub`+`iss`+`roles`+`exp`. Issued bởi auth-service (jjwt lib), verified bởi Kong JWT plugin. Secret nằm trong `.env`, Kong consumer key = `iss` claim = `"zalord"`.
- **OAuth2**: ❌ không có. Hệ này chỉ login bằng phone+password.
- **Keycloak**: ❌ không có. Tự code auth-service.

### Tại sao không Keycloak?

**Pros của Keycloak**: chuẩn OAuth2/OIDC, social login (Google/Facebook), MFA, audit trail, admin UI cho user management.

**Cons trong scope đồ án**:
- Keycloak là **JVM app + cần Postgres riêng** → tốn ~600MB RAM (so với auth-service ~250MB). VPS 8GB của ta đang dùng 6.6GB rồi.
- Đồ án nói chung muốn **show hiểu protocol JWT** chứ không phải plug & play. Tự code = explainable.
- Không có yêu cầu social login từ scope đề bài.

**Khi nào nên Keycloak**: production thật, nhiều client (web + mobile + 3rd party), cần MFA, có team operations.

### Cần làm gì
- Hiện tại: KHÔNG cần. Trong thesis nêu rõ trade-off: "chọn JWT tự code thay vì Keycloak vì scope đồ án ưu tiên giải thích protocol; nếu lên production sẽ migrate".
- **Bonus nếu muốn show OAuth2**: thêm 1 endpoint `POST /api/v1/auth/oauth/google` accept Google ID token, verify chữ ký Google → tạo zalord JWT. Công sức: ~3 giờ. Có thể làm nếu giảng viên hỏi "tại sao không OAuth2".

---

## 12. **ConfigMap + Secret (k8s)** — ❌ không applicable hiện tại, sẽ cần nếu lên k8s

### Trạng thái
- Hiện tại: `.env` file ở repo root, mount vào Compose. Sensitive: `JWT_SECRET`, `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`, `MINIO_ROOT_PASSWORD`. Tất cả env vars.

### Phù hợp không?

ConfigMap + Secret **chỉ tồn tại trong k8s**. Trong Compose không có khái niệm này — `.env` là tương đương. Hiện tại không cần.

**Nếu migrate k8s**: phải tách
- **ConfigMap**: KAFKA_BOOTSTRAP, REDIS_HOST, EVENT_BUS, MINIO_PUBLIC_ENDPOINT
- **Secret**: JWT_SECRET, *_PASSWORD, MINIO_SECRET_KEY

### Cần làm gì
- Hiện tại: KHÔNG. Trong thesis nêu "production sẽ tách Secret (sealed-secrets hoặc Vault) khỏi ConfigMap".
- Nếu giảng viên đòi k8s YAML: 1 ConfigMap + 1 Secret manifest. Công sức: 1 giờ.

---

## 13. **Logging, Monitoring, Tracing** — ❌ hầu như chưa có, **nên thêm Prometheus + Grafana**

| Tool | Trạng thái | Cần không? |
|---|---|---|
| **ELK** (Elasticsearch + Logstash + Kibana) | ❌ chưa | ⚠️ optional — heavy (~1.5GB RAM), với 7 service log đơn giản dùng `docker compose logs` đủ |
| **Prometheus** | ❌ chưa | ✅ **nên có** — cần để benchmark Kafka vs Rabbit |
| **Grafana** | ❌ chưa | ✅ **nên có** — slide thesis screenshot dashboard rất impactful |
| **OpenTelemetry** (tracing phân tán) | ❌ chưa | ⚠️ ích lợi cho debug latency cross-service, nhưng setup tốn |
| **k8s dashboard / Lens** | ❌ N/A | Chưa lên k8s |

### Đề xuất ưu tiên

**Phải có** (1-2 ngày công):
1. **Prometheus** + **Grafana** containers (~250MB RAM tổng)
2. Spring Boot Actuator + `micrometer-registry-prometheus` cho 4 Java service (4 dòng pom + 1 endpoint `/actuator/prometheus`)
3. `prometheus/client_golang` cho 3 Go service
4. RabbitMQ + Kafka có Prometheus exporter built-in
5. Grafana dashboard với 3-4 panel: throughput msg/s, p95 latency, queue depth, JVM heap

→ Đủ để chạy benchmark Kafka vs Rabbit có **screenshot Grafana** cho slide thesis. Đây là yêu cầu **rất nên làm** vì lợi ích/công sức tốt.

**Optional** (nếu dư time):
6. **OpenTelemetry** + **Jaeger** cho tracing — cho phép thấy 1 request đi qua bao nhiêu service và chậm ở đâu. Hữu ích cho "demo trace" lúc defend.

**Không cần**:
- ELK — overkill cho 7 service. Lưu log filesystem qua `docker compose logs --tail` đủ. Nếu muốn aggregate dùng **Loki + Grafana** thay (~150MB).

---

## 14. **Timeout, Circuit Breaker, Recovery** — ⚠️ có timeout, thiếu circuit breaker

| Cơ chế | Trạng thái | Bằng chứng |
|---|---|---|
| **Timeout** | ✅ có ở gRPC clients | `UserGrpcClient` 5s deadline; `MediaGrpcClient` 3s; Redis client 2s read/write |
| **Circuit breaker** | ❌ chưa | Cần Resilience4j (Java) hoặc gobreaker (Go) |
| **Retry** | ⚠️ có ở outbox + broker | Outbox tự retry; RabbitMQ requeue on NACK; **không có retry ở gRPC client** |
| **Backpressure** | ✅ chat-service WS Send channel bounded → drop frame slow consumer |
| **Recovery** | ✅ outbox đảm bảo at-least-once delivery; Redis AOF restore on restart |

### Cần làm thêm — Circuit Breaker

Vì sao cần: nếu media-service down → mỗi `POST /messages` với attachment treo 3s rồi fail. Với 100 req/s → 300 thread block. Circuit breaker phát hiện 5 fail liên tục → **open** → fail-fast trong 30s → cho media-service hồi phục.

**Đề xuất**: thêm `resilience4j-spring-boot3` vào message-service, wrap `MediaGrpcClient.validate()`:
```java
@CircuitBreaker(name = "media", fallbackMethod = "validateFallback")
public Result validate(...) { ... }
```
Công sức: ~3 giờ. Demo: kill media-service trong lúc gửi message → log thấy circuit open + fallback (vd: cho qua không validate, hoặc reject 503).

---

## 15. **CI/CD + GCP** — ❌ chưa có, ⚠️ GCP **không phù hợp** với thiết kế hiện tại

### Trạng thái
- GitHub Actions: **chưa** có workflow nào. Thư mục `.github/` không tồn tại.
- GCP deployment: **chưa**. Đề cương dự án nói "single 8GB VPS via Docker Compose".
- Versioning rollback: Docker image tag = git commit SHA chưa setup; rollback = manual `git revert`.

### GCP phù hợp không?

**Không 100%**. Hệ này thiết kế cho **1 VPS single-node** (Docker Compose, RAM budget 6.6/8GB). Lên GCP đúng nghĩa = chuyển sang **GKE (k8s)** → phải viết lại toàn bộ manifest, đo lại RAM, viết Helm chart...

**Nếu giảng viên yêu cầu cloud**: 
- Option A nhẹ: deploy lên 1 GCE VM (e2-standard-2, ~$25/tháng), chạy Compose như hiện tại. Chỉ tốn setup ssh + Docker.
- Option B đầy đủ: GKE Autopilot, cần convert sang k8s + service mesh. ~5 ngày công.

### Cần làm thêm — CI tối thiểu

**Nên có** (~3 giờ):

`.github/workflows/ci.yml`:
```yaml
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build Java services
        run: |
          for svc in auth user message group media; do
            cd backend/$svc-service && ./mvnw -B -DskipTests package && cd -
          done
      - name: Build Go services
        run: |
          for svc in user chat notification; do
            cd backend/$svc-service && go build ./... && cd -
          done
      - name: Docker compose validate
        run: docker compose config
```

**CD lên VPS** (~2 giờ):
- SSH key vào VPS
- Workflow trigger trên tag `v*.*.*`: ssh tới VPS, `git pull && docker compose up -d --build`
- Rollback: `git checkout <previous-tag> && docker compose up -d`

**Image registry**: build image trong CI, push GHCR (GitHub Container Registry — free cho public repo). Tag bằng commit SHA. Rollback = pull tag cũ.

---

## x. **Recovery scenario** — ✅ đã có nền tảng

| Failure | Cách hệ phục hồi |
|---|---|
| message-service crash giữa khi gửi | Tx rollback → message KHÔNG lưu. Client retry (lấy 5xx). |
| message-service crash SAU INSERT messages + outbox | Restart → OutboxScheduler poll unpublished → ship → consumers idempotent dedupe |
| RabbitMQ down | Outbox row giữ `published_at = NULL` → retry vĩnh viễn cho đến khi broker lên |
| chat-service crash | WS rớt → FE auto reconnect; missed message recovered qua `GET /messages` |
| Redis crash | AOF restore on restart (mỗi 1s flush). Refresh tokens + sessions còn nguyên. Cache miss = fallback DB |
| media-service crash | gRPC client báo UNAVAILABLE → message-service trả 500. **Cần circuit breaker** (xem mục 14) |
| Postgres data loss | Backup chưa có → ⚠️ nên thêm `pg_dump` cron |

### Cần làm thêm
- **DB backup** (~1 giờ): cron container chạy `pg_dump` mỗi 6h vào volume, retain 7 ngày.
- **Circuit breaker** (xem mục 14)

---

## 📊 Bảng tổng hợp

| # | Yêu cầu | Trạng thái | Phù hợp? | Ưu tiên thêm |
|---|---|---|---|---|
| 1 | Chứng minh không monolith | ✅ đầy đủ luận điểm | ✅ | — |
| 2 | DDD bounded context | ✅ 7 BC rõ ràng | ✅ | — |
| 3 | SRP + cohesion + coupling | ✅ phần lớn | ✅ | message-service hơi nặng, OK |
| 4 | Sync/async/event-driven | ✅ 3 tier rõ | ✅ | — |
| 5 | Endpoint design + Swagger | ✅ phần lớn | ✅ | **filtering** (P3, 2h) |
| 6 | DB per service + consistency + saga | ✅ outbox+CQRS+eventual | ✅ | — |
| 7 | Outbox + CQRS + polyglot | ✅ cả ba | ✅ | — |
| 8 | (instruction, N/A) | — | — | — |
| 9 | Service discovery + LB | ❌ chưa | ⚠️ Compose chưa cần; nếu k8s thì built-in | k8s manifest (P2, 1 ngày) — nếu giảng viên đòi |
| 10 | API Gateway features | ⚠️ thiếu rate limit | ✅ | **Rate limiting** (P1, 30 phút) |
| 11 | JWT/OAuth2/Keycloak | ✅ JWT đủ | ✅ | OAuth2 endpoint demo (P3, 3h) optional |
| 12 | ConfigMap/Secret | ❌ N/A (Compose) | ❌ trừ khi k8s | — |
| 13 | Logging/Monitoring/Tracing | ❌ thiếu | ⚠️ Prometheus + Grafana **rất nên có** | **P1, 1-2 ngày** |
| 14 | Timeout/Circuit breaker | ⚠️ thiếu CB | ✅ | **Resilience4j** (P1, 3h) |
| 15 | CI/CD + GCP | ❌ chưa | ⚠️ CI nên có; GCP không phù hợp | **GH Actions CI** (P2, 3h); CD lên VPS (P3, 2h) |
| x | Recovery scenario | ✅ phần lớn | ✅ | DB backup cron (P2, 1h) |

---

## 🎯 Lộ trình đề xuất (ưu tiên cao → thấp)

### Tuần 1 — Polish phần đã có
1. ✅ Hoàn thành DOCUMENT.md, realtime-features.md, message-recall-reply.md (xong)
2. **Rate limiting Kong** trên `/auth/login` + `/auth/register` (30 phút)
3. **Circuit breaker** Resilience4j trên `MediaGrpcClient` + `UserGrpcClient` (3 giờ)
4. **Filtering** cho `GET /messages?before=`, `GET /notifications?type=` (2 giờ)
5. **DB backup cron** container chạy `pg_dump` (1 giờ)

### Tuần 2 — Observability cho thesis
6. **Prometheus + Grafana** stack containers (2 giờ)
7. Spring Boot Actuator + Micrometer cho 4 Java service (1 giờ)
8. `prometheus/client_golang` cho 3 Go service (2 giờ)
9. Grafana dashboard: throughput, latency, queue depth, JVM heap (3 giờ)
10. **Benchmark Kafka vs Rabbit** với Prometheus capture (1 ngày) — output cho slide thesis

### Tuần 3 — CI/CD + (optional) k8s
11. **GitHub Actions CI** build + test (3 giờ)
12. CD ssh lên VPS deploy tag (2 giờ)
13. (Optional) k8s manifest minimal — Deployment + Service per microservice + 1 Ingress (1-2 ngày)

### KHÔNG làm (giải thích trong thesis nếu hỏi)
- ❌ Keycloak — overkill cho scope; JWT đủ chứng minh hiểu protocol
- ❌ ELK — heavy; nếu muốn log aggregation dùng Loki + Grafana
- ❌ Eureka/Consul — Compose dùng Docker DNS; k8s tự có Service discovery
- ❌ Client-side LB — server-side qua k8s Service đủ tốt
- ❌ 2PC across services — outbox + idempotent consumer là pattern đúng cho microservice
- ❌ GCP/GKE đầy đủ — design hiện tại cho single VPS; nếu cần cloud dùng GCE VM chạy Compose

---

## 📝 Lời khuyên defend trước hội đồng

3 câu trả lời "vàng" cần luyện:

1. **"Vì sao không Keycloak?"** → "Để show hiểu JWT protocol từ scratch; Keycloak quá heavy cho 8GB VPS. Production sẽ migrate."

2. **"Vì sao không 2PC / saga đầy đủ?"** → "Microservice với DB-per-service không thể 2PC. Outbox + idempotent consumer cho eventual consistency là pattern đúng. Mọi flow của hệ đều chịu được eventual."

3. **"Vì sao 3 tier giao tiếp khác nhau, không chuẩn hóa 1 thứ?"** → "Vì 3 tier giải quyết 3 trade-off khác nhau: sync gRPC = strong consistency low volume, EventBus = high volume fan-out, direct broker = không cần benchmark. Một pattern không fit cho cả ba."

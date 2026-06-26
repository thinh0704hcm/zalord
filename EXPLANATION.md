# Giải thích: Requirements không phù hợp với hệ thống Zalord

Tài liệu này giải thích những requirement trong checklist microservices mà **không phù hợp** hoặc **không áp dụng được** đối với kiến trúc và ngữ cảnh cụ thể của hệ thống Zalord.

---

## 1. Saga Pattern (Requirement 6 — một phần)

**Tại sao không phù hợp:**

Saga pattern giải quyết bài toán *distributed transaction* phức tạp — khi một business flow cần cập nhật dữ liệu trên nhiều service và cần có *compensating transaction* (rollback phân tán) nếu một bước thất bại.

Zalord không có flow nào đủ phức tạp để cần saga. Flow cross-service duy nhất là **user registration** (2 bước: auth-service tạo account → user-service tạo profile), được giải quyết bằng **synchronous gRPC:

- Nếu gRPC call thất bại: auth-service có `createProfileFallback()`, không có partial state nào bị rò rỉ.
- Nếu circuit breaker open: trả về error ngay, không có distributed state cần rollback.

Áp dụng saga orchestrator hay choreography cho 2 bước đơn giản này sẽ là **overengineering** — tăng độ phức tạp vận hành mà không mang lại giá trị thực sự. Saga phù hợp khi có flow như: `order-service → payment-service → inventory-service → shipping-service` — chuỗi 4+ bước có compensating transaction thực sự.

---

## 2. OAuth2 / Keycloak (Requirement 11)

**Tại sao không phù hợp:**

**OAuth2** là giao thức *delegated authorization* — thiết kế cho trường hợp một ứng dụng (client) muốn truy cập tài nguyên của người dùng trên *một service khác* (resource server) mà không cần biết mật khẩu. Ví dụ điển hình: "Đăng nhập bằng Google" — ứng dụng của bạn delegate authentication cho Google, sau đó truy cập Google Calendar của user.

**Zalord là closed, first-party system**: người dùng chỉ đăng nhập vào *chính* hệ thống Zalord. Không tồn tại bên thứ ba nào cần delegate access. Không có use case OAuth2 nào trong hệ thống này.

**Keycloak** là enterprise-grade Identity Provider (IdP) dành cho môi trường nhiều ứng dụng cần SSO (Single Sign-On), quản lý user tập trung, social login. Đối với Zalord:

- Keycloak cần tối thiểu 512 MB–1 GB RAM riêng chỉ để khởi động
- Thêm dependency lớn vào hệ thống, tăng độ phức tạp deploy và cấu hình
- Không giải quyết vấn đề gì mà custom `auth-service` (Spring Boot + JWT HS256) chưa giải quyết

**Giải pháp đã dùng phù hợp hơn:** JWT HS256 do `auth-service` phát hành, Kong validate tại edge bằng built-in `jwt` plugin, inject `X-User-Id` / `X-User-Roles` headers cho tất cả downstream services. Downstream services trust headers — không cần parse JWT.

---

## 3. ELK Stack (Requirement 13 — một phần)

**Tại sao không phù hợp ở quy mô này:**

ELK Stack (Elasticsearch + Logstash + Kibana) là giải pháp log aggregation cho môi trường sản xuất quy mô lớn (hàng triệu log lines/day). Yêu cầu tài nguyên tối thiểu:

| Component | RAM |
|---|---|
| Elasticsearch | 2–4 GB |
| Logstash | ~512 MB |
| Kibana | ~512 MB |
| **Tổng** | **~3–5 GB** |

VPS đang chạy 7 business services + Kafka + PostgreSQL×6 + Redis + MinIO + k3s không còn tài nguyên để cấp thêm cho ELK.

**Approach đúng theo 12-factor app principles:**

- Mỗi service log ra stdout (structured JSON) — **đã làm**: Go services dùng `go.uber.org/zap`, Java services dùng SLF4J/Logback
- Log aggregation là concern của infrastructure (không phải application)
- Prometheus + Grafana đã đủ cho metrics-based observability trong scope này

**Alternative nhẹ hơn nếu cần log aggregation**: Grafana Loki (~200 MB RAM) thay vì Elasticsearch — tích hợp native với Grafana đã có.

---

## 4. K8s Ingress Controller resource (Requirement 10 — khái niệm cần làm rõ)

**Tại sao Kong không được deploy dưới dạng K8s Ingress resource:**

Có hai cách dùng Kong với Kubernetes:

1. **Kong Ingress Controller**: Deploy Kong xử lý `networking.k8s.io/v1 Ingress` resources, dùng CRD (KongIngress, KongPlugin...) để cấu hình. Phù hợp khi muốn tích hợp sâu vào K8s API.

2. **Kong standalone Gateway** (cách Zalord dùng): Kong chạy như một Deployment bình thường trong namespace `zalord-dev`, cấu hình bằng declarative YAML (DB-less mode `kong.yml`). Không có `Ingress` resource nào trong cluster.

**Lý do chọn standalone:**

- DB-less declarative config (`infra/kong/kong.yml`) dễ version control, hoạt động giống nhau cho cả Docker Compose (dev) lẫn K8s (prod) — **same config, both environments**
- Không cần cài CRD phức tạp vào cluster k3s
- Lua `pre-function` plugin (identity injection, header stripping) phức tạp hơn những gì annotation của `nginx-ingress` hay `traefik` CRD có thể express
- Kong rate-limiting plugin với per-route override (auth routes: 10/min, global: 600/min) dễ cấu hình trong YAML declarative

**Kong vẫn là ingress point** duy nhất của hệ thống — tất cả traffic từ ngoài đều đi qua Kong. Chỉ là không thông qua K8s Ingress API mà là thông qua Kong's own routing table.

---

## Tóm tắt

| Requirement | Trạng thái | Lý do không phù hợp |
|---|---|---|
| Saga Pattern | Không áp dụng | Không có distributed transaction đủ phức tạp; 2-step registration xử lý bằng gRPC + circuit breaker |
| OAuth2 / Keycloak | Không phù hợp | Closed first-party system, không có third-party delegation use case |
| ELK Stack | Không triển khai | Resource constraint trên VPS; stdout logging đúng 12-factor; Prometheus+Grafana đủ |
| K8s Ingress resource | Kong standalone | DB-less Kong config portable hơn, dùng chung cho dev (Compose) và prod (K8s) |

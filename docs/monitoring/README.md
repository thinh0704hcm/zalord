# Monitoring — Index

Tài liệu liên quan tới quan sát hệ thống và benchmark broker.

**Kết luận benchmark (TL;DR)**: ở rate hệ Zalord cần (~200-500 tin/s), **RabbitMQ end-to-end nhanh hơn Kafka ~3-5ms p99** (push model vs pull model). Cả 2 đạt 100% delivery, 0 mất tin. Chi tiết [benchmark.md §8](./benchmark.md#phần-8--kết-quả-benchmark-thực-tế-đã-đo).

| File | Mô tả | Đọc khi |
|---|---|---|
| [observability.md](./observability.md) | Prometheus + Grafana stack, dashboard tour, demo workflow (rate-limit, circuit breaker, broker lag), PromQL cookbook, production hardening | Setup lần đầu, học cách dùng dashboard, muốn thêm metric mới |
| [benchmark.md](./benchmark.md) | Hướng dẫn chi tiết benchmark **RabbitMQ vs Kafka** end-to-end + [kết quả đã đo §8](./benchmark.md#phần-8--kết-quả-benchmark-thực-tế-đã-đo) | Chạy load test, lấy số liệu thesis, đọc kết quả benchmark |
| [tracing.md](./tracing.md) | **OpenTelemetry + Jaeger** distributed tracing — concept, code wire-up, propagation, CLI recipe | Hiểu cách hoạt động + setup, debug deep |
| [find-trace-ui.md](./find-trace-ui.md) | **Hands-on UI** — 5 cách tìm trace 1 request bằng Jaeger UI (không cần CLI) | Vừa gửi 1 request, muốn xem nó đi qua những service nào |

## Quick start

```bash
# Stack up + dashboard
make dev
open http://localhost:3000/d/zalord-overview     # Grafana (metrics)
open http://localhost:9090                        # Prometheus (raw query)
open http://localhost:16686                       # Jaeger (traces)

# Benchmark
RESET=1 RATE=200 DURATION=120 scripts/run-benchmark-rabbit.sh \
 &&     RATE=200 DURATION=120 scripts/run-benchmark-kafka.sh   # 2 backend, 5 phút
```

## Code liên quan

| Layer | File |
|---|---|
| Scrape config | [infra/prometheus/prometheus.yml](../../infra/prometheus/prometheus.yml) |
| Dashboard JSON | [infra/grafana/dashboards/zalord-overview.json](../../infra/grafana/dashboards/zalord-overview.json) |
| Datasource provisioning | [infra/grafana/provisioning/datasources/prometheus.yaml](../../infra/grafana/provisioning/datasources/prometheus.yaml) |
| Java metrics binding | `backend/{auth,message}-service/src/main/java/.../config/MetricsConfig.java` |
| Go metrics middleware | `backend/{chat,notification,user}-service/pkg/metrics/metrics.go` |
| Benchmark script (Python) | [scripts/benchmark-e2e.py](../../scripts/benchmark-e2e.py) |
| Benchmark wrapper — RabbitMQ | [scripts/run-benchmark-rabbit.sh](../../scripts/run-benchmark-rabbit.sh) |
| Benchmark wrapper — Kafka | [scripts/run-benchmark-kafka.sh](../../scripts/run-benchmark-kafka.sh) |
| Benchmark results (JSON) | `scripts/benchmark-results.json` |
| OTel collector config | [infra/otel/otelcol-config.yaml](../../infra/otel/otelcol-config.yaml) |
| Kong tracing plugin | [infra/kong/kong.yml](../../infra/kong/kong.yml) (search `opentelemetry`) |

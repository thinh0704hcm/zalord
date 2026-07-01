# Monitoring — Index

Tài liệu liên quan tới quan sát hệ thống và benchmark broker.

**Kết luận benchmark (TL;DR)**: ở rate hệ Zalord cần (~200-500 tin/s), **RabbitMQ end-to-end nhanh hơn Kafka ~3-5ms p99** (push model vs pull model). Cả 2 đạt 100% delivery, 0 mất tin. Chi tiết [benchmark.md §8](./benchmark.md#phần-8--kết-quả-benchmark-thực-tế-đã-đo).

| File | Mô tả | Đọc khi |
|---|---|---|
| [observability.md](./observability.md) | Prometheus + Grafana stack, dashboard tour, demo workflow (rate-limit, circuit breaker, broker lag), PromQL cookbook | **Metrics** — dashboard, số liệu real-time |
| [benchmark.md](./benchmark.md) | Hướng dẫn chi tiết benchmark **RabbitMQ vs Kafka** end-to-end + [kết quả đã đo §8](./benchmark.md#phần-8--kết-quả-benchmark-thực-tế-đã-đo) | Chạy load test, lấy số liệu thesis |
| [tracing.md](./tracing.md) | **OpenTelemetry + Jaeger** concept + wire-up (Java agent, Go SDK, Kong plugin, RabbitMQ propagation) | **Traces** — hiểu cách hoạt động + setup |
| [find-trace-ui.md](./find-trace-ui.md) | Hands-on 5 cách tìm trace 1 request bằng Jaeger UI + [demo golden trace cho defense](./find-trace-ui.md#demo-thesis--golden-trace-post--broker--consumer-trong-1-trace-duy-nhất) | Debug 1 request cụ thể, defense demo |
| [logging.md](./logging.md) | **Loki + Promtail + Grafana Explore** — centralized logging cho 28 container, LogQL, correlate log↔trace | **Logs** — debug, tìm error theo user/service |

## Quick start

```bash
# Stack up + observability tools
make dev
open http://localhost:3000/d/zalord-overview     # Grafana metrics dashboard
open http://localhost:3000/explore                # Grafana Explore (Loki logs, Jaeger traces)
open http://localhost:9090                        # Prometheus (raw query)
open http://localhost:16686                       # Jaeger UI (traces)
open http://localhost:3100                        # Loki API (rarely direct — use Grafana)

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
| Loki config | [infra/loki/loki-config.yaml](../../infra/loki/loki-config.yaml) |
| Promtail scrape config | [infra/promtail/promtail-config.yaml](../../infra/promtail/promtail-config.yaml) |
| Grafana Loki datasource + trace link | [infra/grafana/provisioning/datasources/loki.yaml](../../infra/grafana/provisioning/datasources/loki.yaml) |
| Grafana Jaeger datasource | [infra/grafana/provisioning/datasources/jaeger.yaml](../../infra/grafana/provisioning/datasources/jaeger.yaml) |

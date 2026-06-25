package metrics

import (
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

// HTTP server metrics — names mirror Spring Boot's Actuator conventions
// (http_server_requests_seconds_*) so Grafana dashboards can reuse the same
// PromQL across Java + Go services with only a service-name label split.
var (
	httpRequestsTotal = promauto.NewCounterVec(
		prometheus.CounterOpts{
			Name: "http_server_requests_total",
			Help: "Total HTTP requests handled.",
		},
		[]string{"method", "path", "status"},
	)
	httpRequestDuration = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "http_server_requests_seconds",
			Help:    "Duration of HTTP requests in seconds.",
			Buckets: []float64{0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10},
		},
		[]string{"method", "path"},
	)
)

// Middleware records every request the Gin router serves. Use FullPath() to
// label by route template, not the raw URL — otherwise every unique URL would
// blow up cardinality.
func Middleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()
		path := c.FullPath()
		if path == "" {
			// Unmatched route (404). Group them under one label so the metric
			// stays bounded.
			path = "unmatched"
		}
		httpRequestsTotal.
			WithLabelValues(c.Request.Method, path, strconv.Itoa(c.Writer.Status())).
			Inc()
		httpRequestDuration.
			WithLabelValues(c.Request.Method, path).
			Observe(time.Since(start).Seconds())
	}
}

// Handler exposes the default Go process + custom collectors as /metrics.
func Handler() gin.HandlerFunc {
	h := promhttp.Handler()
	return func(c *gin.Context) { h.ServeHTTP(c.Writer, c.Request) }
}

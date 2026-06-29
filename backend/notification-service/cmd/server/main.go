package main

import (
	"context"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"
	"github.com/gin-gonic/gin"
	queries "github.com/thinh0704hcm/zalord/backend/notification-service/db/sqlc"
	docs "github.com/thinh0704hcm/zalord/backend/notification-service/docs"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/config"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/database"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/handler"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/repository"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/service"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/eventbus"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/metrics"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/mq"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/otelx"
	"go.uber.org/zap"
)

// @title           Notification Service API
// @version         v1
// @description     Per-user notification feed (the bell icon).
// @BasePath        /
// @securityDefinitions.apikey  BearerAuth
// @in                          header
// @name                        Authorization
// @description                 Paste the access token only — Swagger adds "Bearer ".
func main() {
	cfg := config.Load()
	if err := logger.Init(); err != nil {
		return
	}
	defer logger.Sync()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// OTel tracing (reads OTEL_EXPORTER_OTLP_ENDPOINT from env).
	otelShutdown, err := otelx.Init(ctx, "notification-service")
	if err != nil {
		logger.Log.Fatal("otel init failed", zap.Error(err))
	}
	defer func() { _ = otelShutdown(context.Background()) }()

	pool, err := database.Connect(ctx, cfg.DbUri)
	if err != nil {
		logger.Log.Fatal("db connect failed", zap.Error(err))
	}
	defer pool.Close()

	// ─── Two consumer paths ───────────────────────────────────────────────
	// (1) message.created  → EventBus (RabbitMQ or Kafka, configurable).
	//     This is the chain we benchmark — high write volume.
	// (2) group.*          → direct RabbitMQ (group-service is not on the
	//     abstracted path, so we consume it natively).
	//
	// Both paths can co-exist when EVENT_BUS=kafka: the RabbitMQ connection
	// below still serves group events; only message.created routes via Kafka.

	bus, err := eventbus.New(cfg.MqUri, os.Getenv("KAFKA_BOOTSTRAP"))
	if err != nil {
		logger.Log.Fatal("eventbus init failed", zap.Error(err))
	}
	defer func() { _ = bus.Close() }()

	rmq, err := mq.NewRabbitMQ(cfg.MqUri)
	if err != nil {
		logger.Log.Fatal("rabbitmq connect failed", zap.Error(err))
	}
	defer func() { _ = rmq.Close() }()

	// Group queue topology only — message queue handled by eventbus now.
	{
		ch, err := rmq.Channel()
		if err != nil {
			logger.Log.Fatal("open channel failed", zap.Error(err))
		}
		if err := mq.SetupGroupTopology(ch); err != nil {
			logger.Log.Fatal("group topology failed", zap.Error(err))
		}
		_ = ch.Close()
	}

	// Wiring
	q := queries.New(pool)
	repo := repository.New(q)
	svc := service.New(repo)
	h := handler.New(svc)

	// (1) message.created via EventBus
	if err := bus.Subscribe(ctx, "message.created", "notification-message",
		func(ctx context.Context, body []byte) error {
			return svc.HandleMessageEvent(ctx, "message.created", body)
		}); err != nil {
		logger.Log.Fatal("eventbus subscribe failed", zap.Error(err))
	}

	// (2) group.* via raw RabbitMQ consumer
	consumer := mq.NewConsumer(rmq)
	if err := consumer.Consume(ctx, mq.GroupQueue, svc.HandleGroupEvent); err != nil {
		logger.Log.Fatal("consume group failed", zap.Error(err))
	}

	// HTTP
	r := gin.Default()
	r.Use(otelgin.Middleware("notification-service"))
	r.Use(metrics.Middleware())
	// Prometheus scrape endpoint — direct docker-network access, not via Kong.
	r.GET("/metrics", metrics.Handler())
	r.GET("/health", func(c *gin.Context) { c.JSON(http.StatusOK, gin.H{"status": "ok"}) })

	docs.SwaggerInfo.BasePath = "/"
	r.GET("/v3/api-docs", func(c *gin.Context) {
		c.Data(http.StatusOK, "application/json", []byte(docs.SwaggerInfo.ReadDoc()))
	})

	// Public version endpoint (no auth) — used for canary demo
	r.GET("/api/v1/notifications/version", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"version": "v1",
			"service": "notification-service",
			"canary":  false,
		})
	})

	api := r.Group("/api/v1/notifications")
	api.Use(middleware.Identity())
	{
		api.GET("", h.List)
		api.GET("/unread-count", h.UnreadCount)
		api.POST("/:id/read", h.MarkRead)
		api.POST("/read-all", h.MarkAllRead)
	}

	logger.Log.Info("starting notification-service", zap.String("port", cfg.ServerPort))
	if err := r.Run(":" + cfg.ServerPort); err != nil {
		logger.Log.Fatal("server failed", zap.Error(err))
	}
}

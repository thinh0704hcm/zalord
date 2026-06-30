package main

import (
	"context"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/gin-gonic/gin"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/config"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/events"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/handler"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/membership"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/presence"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/eventbus"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/metrics"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/otelx"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/redisx"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"
	"go.uber.org/zap"
)

func main() {
	cfg := config.Load()
	if err := logger.Init(); err != nil {
		return
	}
	defer logger.Sync()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// OTel tracing (reads OTEL_EXPORTER_OTLP_ENDPOINT from env).
	otelShutdown, err := otelx.Init(ctx, "chat-service")
	if err != nil {
		logger.Log.Fatal("otel init failed", zap.Error(err))
	}
	defer func() { _ = otelShutdown(context.Background()) }()

	// Redis powers the membership cache (read), presence registry, and presence
	// pub/sub. All three are best-effort: a Redis outage degrades realtime
	// features but doesn't break chat-service's core message fan-out.
	rdb, err := redisx.New(ctx)
	if err != nil {
		logger.Log.Fatal("redis connect failed", zap.Error(err))
	}
	defer func() { _ = rdb.Close() }()

	bus, err := eventbus.New(cfg.MqUri, os.Getenv("KAFKA_BOOTSTRAP"))
	if err != nil {
		logger.Log.Fatal("eventbus init failed", zap.Error(err))
	}
	defer func() { _ = bus.Close() }()

	reg := session.NewRegistry()
	memCache := membership.New(rdb)
	pres := presence.New(rdb)
	router := handler.NewRouter(reg, memCache, pres)
	wsHandler := handler.NewWsHandler(reg, router, pres)

	// Subscribe to message.created via the chosen backend.
	fanOut := events.NewFanOut(reg)
	if err := bus.Subscribe(ctx, "message.created", "chat-fanout", fanOut.Handle); err != nil {
		logger.Log.Fatal("subscribe message.created failed", zap.Error(err))
	}

	// Read receipts ride on a separate consumer group so they're independent
	// of message.created — a slow read-receipt handler can't back up message
	// delivery.
	readFanOut := events.NewReadReceiptFanOut(reg, memCache)
	if err := bus.Subscribe(ctx, "message.read", "chat-read-receipt", readFanOut.Handle); err != nil {
		logger.Log.Fatal("subscribe message.read failed", zap.Error(err))
	}

	// Recalls — same rationale for the dedicated consumer group.
	recallFanOut := events.NewRecallFanOut(reg, memCache)
	if err := bus.Subscribe(ctx, "message.recalled", "chat-recall", recallFanOut.Handle); err != nil {
		logger.Log.Fatal("subscribe message.recalled failed", zap.Error(err))
	}

	// Group events
	groupAddedFanOut := events.NewGroupEventFanOut(reg, memCache, "group.member.added")
	if err := bus.Subscribe(ctx, "group.member.added", "chat-group-added", groupAddedFanOut.Handle); err != nil {
		logger.Log.Fatal("subscribe group.member.added failed", zap.Error(err))
	}

	groupRemovedFanOut := events.NewGroupEventFanOut(reg, memCache, "group.member.removed")
	if err := bus.Subscribe(ctx, "group.member.removed", "chat-group-removed", groupRemovedFanOut.Handle); err != nil {
		logger.Log.Fatal("subscribe group.member.removed failed", zap.Error(err))
	}

	// Presence relay runs as a goroutine: listens to Redis pub/sub for
	// transitions on ANY instance and pushes to local watchers.
	relay := events.NewPresenceRelay(pres, router)
	go relay.Run(ctx)

	r := gin.Default()
	r.Use(otelgin.Middleware("chat-service"))
	r.Use(metrics.Middleware())
	// Prometheus scrape endpoint. Not behind auth — Prometheus scrapes
	// directly via the docker network, never via Kong.
	r.GET("/metrics", metrics.Handler())
	r.GET("/health", func(c *gin.Context) {
		users, conns := reg.Stats()
		c.JSON(http.StatusOK, gin.H{
			"status":           "ok",
			"online_users":     users,
			"open_connections": conns,
		})
	})

	r.GET("/api/v1/chat/canary", func(c *gin.Context) {
		if os.Getenv("CANARY") != "true" {
			c.Status(http.StatusNotFound)
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"id":     1,
			"title":  "Lorem Ipsum",
			"body":   "Dolor sit amet, consectetur adipiscing elit.",
			"active": true,
		})
	})

	ws := r.Group("/ws")
	ws.Use(middleware.Identity())
	{
		ws.GET("/chat", wsHandler.Connect)
	}

	logger.Log.Info("starting chat-service", zap.String("port", cfg.ServerPort))
	if err := r.Run(":" + cfg.ServerPort); err != nil {
		logger.Log.Fatal("server failed", zap.Error(err))
	}
}

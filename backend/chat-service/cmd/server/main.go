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
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/eventbus"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
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

	// EventBus — backend chosen by EVENT_BUS env (rabbitmq | kafka).
	// Must match message-service so both ends speak the same broker.
	bus, err := eventbus.New(cfg.MqUri, os.Getenv("KAFKA_BOOTSTRAP"))
	if err != nil {
		logger.Log.Fatal("eventbus init failed", zap.Error(err))
	}
	defer func() { _ = bus.Close() }()

	// Registry + handlers
	reg := session.NewRegistry()
	wsHandler := handler.NewWsHandler(reg)
	fanOut := events.NewFanOut(reg)

	// Subscribe to message.created via the chosen backend.
	if err := bus.Subscribe(ctx, "message.created", "chat-fanout", fanOut.Handle); err != nil {
		logger.Log.Fatal("subscribe failed", zap.Error(err))
	}

	// HTTP / WebSocket server
	r := gin.Default()
	r.GET("/health", func(c *gin.Context) {
		users, conns := reg.Stats()
		c.JSON(http.StatusOK, gin.H{
			"status":           "ok",
			"online_users":     users,
			"open_connections": conns,
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

package main

import (
	"context"
	"net/http"
	"os/signal"
	"syscall"

	"github.com/gin-gonic/gin"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/config"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/events"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/handler"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/mq"
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

	// RabbitMQ
	rmq, err := mq.NewRabbitMQ(cfg.MqUri)
	if err != nil {
		logger.Log.Fatal("rabbitmq connect failed", zap.Error(err))
	}
	defer func() { _ = rmq.Close() }()

	// Topology (idempotent; matches message-service publisher)
	{
		ch, err := rmq.Channel()
		if err != nil {
			logger.Log.Fatal("open channel failed", zap.Error(err))
		}
		if err := mq.SetupTopology(ch); err != nil {
			logger.Log.Fatal("topology setup failed", zap.Error(err))
		}
		_ = ch.Close()
	}

	// Registry + handlers
	reg := session.NewRegistry()
	wsHandler := handler.NewWsHandler(reg)
	fanOut := events.NewFanOut(reg)

	// Consumer (background; respects ctx)
	consumer := mq.NewConsumer(rmq)
	if err := consumer.Consume(ctx, mq.ChatDeliveryQueue, fanOut.Handle); err != nil {
		logger.Log.Fatal("consume failed", zap.Error(err))
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

	// /ws/chat is HTTP from Kong's POV (the upgrade) — Identity middleware
	// reads the injected header during the handshake.
	ws := r.Group("/ws")
	ws.Use(middleware.Identity())
	{
		ws.GET("/chat", wsHandler.Connect)
	}

	logger.Log.Info("starting chat-service",
		zap.String("port", cfg.ServerPort))
	if err := r.Run(":" + cfg.ServerPort); err != nil {
		logger.Log.Fatal("server failed", zap.Error(err))
	}
}

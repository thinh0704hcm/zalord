package main

import (
	"context"
	"net/http"
	"os/signal"
	"syscall"

	"github.com/gin-gonic/gin"
	queries "github.com/thinh0704hcm/zalord/backend/notification-service/db/sqlc"
	docs "github.com/thinh0704hcm/zalord/backend/notification-service/docs"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/config"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/database"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/handler"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/repository"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/service"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/mq"
	"go.uber.org/zap"
)

// @title           Notification Service API
// @version         v1
// @description     Per-user notification feed (the bell icon). Built by consuming message.created + group.* events; distinct from CQRS inbox.
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

	pool, err := database.Connect(ctx, cfg.DbUri)
	if err != nil {
		logger.Log.Fatal("db connect failed", zap.Error(err))
	}
	defer pool.Close()

	rmq, err := mq.NewRabbitMQ(cfg.MqUri)
	if err != nil {
		logger.Log.Fatal("rabbitmq connect failed", zap.Error(err))
	}
	defer func() { _ = rmq.Close() }()

	{
		ch, err := rmq.Channel()
		if err != nil {
			logger.Log.Fatal("open channel failed", zap.Error(err))
		}
		if err := mq.SetupTopology(ch); err != nil {
			logger.Log.Fatal("topology failed", zap.Error(err))
		}
		_ = ch.Close()
	}

	// Wiring
	q := queries.New(pool)
	repo := repository.New(q)
	svc := service.New(repo)
	h := handler.New(svc)

	// Two consumers — one per source exchange.
	consumer := mq.NewConsumer(rmq)
	if err := consumer.Consume(ctx, mq.MessageQueue, svc.HandleMessageEvent); err != nil {
		logger.Log.Fatal("consume message failed", zap.Error(err))
	}
	if err := consumer.Consume(ctx, mq.GroupQueue, svc.HandleGroupEvent); err != nil {
		logger.Log.Fatal("consume group failed", zap.Error(err))
	}

	// HTTP
	r := gin.Default()
	r.GET("/health", func(c *gin.Context) { c.JSON(http.StatusOK, gin.H{"status": "ok"}) })

	docs.SwaggerInfo.BasePath = "/"
	r.GET("/v3/api-docs", func(c *gin.Context) {
		c.Data(http.StatusOK, "application/json", []byte(docs.SwaggerInfo.ReadDoc()))
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

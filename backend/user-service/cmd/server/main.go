package main

import (
	"context"
	"net/http"
	"os/signal"
	"syscall"

	"github.com/gin-gonic/gin"
	queries "github.com/thinh0704hcm/zalord/backend/user-service/db/sqlc"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/database"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/handler"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/repository"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/service"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/config"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/mq"
	"go.uber.org/zap"
)

func main() {
	cfg := config.Load()

	if err := logger.Init(); err != nil {
		return
	}
	defer logger.Sync()

	// Cancelled on SIGINT/SIGTERM — consumer goroutine exits cleanly.
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// Postgres
	pool, err := database.Connect(ctx, cfg.DbUri)
	if err != nil {
		logger.Log.Fatal("database connect failed", zap.Error(err))
	}
	defer pool.Close()

	// RabbitMQ
	rmq, err := mq.NewRabbitMQ(cfg.MqUri)
	if err != nil {
		logger.Log.Fatal("rabbitmq connect failed", zap.Error(err))
	}
	defer func() { _ = rmq.Close() }()

	// Topology — declared on a one-shot channel so we don't keep it open.
	{
		ch, err := rmq.Channel()
		if err != nil {
			logger.Log.Fatal("rabbitmq: open channel failed", zap.Error(err))
		}
		if err := mq.SetupTopology(ch); err != nil {
			logger.Log.Fatal("rabbitmq: setup topology failed", zap.Error(err))
		}
		_ = ch.Close()
	}

	// Wiring
	query := queries.New(pool)
	profileRepo := repository.NewProfileRepository(query)
	profileService := service.NewProfileService(profileRepo)
	profileHandler := handler.NewProfileHandler(profileService)

	// Consumer (background goroutine; respects ctx)
	consumer := mq.NewConsumer(rmq)
	if err := consumer.Consume(ctx, mq.UserQueue, profileService.ConsumeProfileCreated); err != nil {
		logger.Log.Fatal("rabbitmq: consume profile failed", zap.Error(err))
	}

	// HTTP
	r := gin.Default()
	r.GET("/health", func(c *gin.Context) { c.JSON(http.StatusOK, gin.H{"status": "ok"}) })

	api := r.Group("/api/v1/users")
	api.Use(middleware.Identity())
	{
		api.GET("/me", profileHandler.GetMe)
	}

	logger.Log.Info("starting http server", zap.String("port", cfg.ServerPort))
	if err := r.Run(":" + cfg.ServerPort); err != nil {
		logger.Log.Fatal("server failed", zap.Error(err))
	}
}

package main

import (
	"context"
	"net/http"
	"os/signal"
	"syscall"

	"github.com/gin-gonic/gin"

	"github.com/thinh0704hcm/zalord/backend/user-service/internal/database"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/config"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/mq"
	"go.uber.org/zap"
)

func main() {
	// 1. Load config
	cfg := config.Load()

	// 2. Logger
	if err := logger.Init(); err != nil {
		return
	}
	defer logger.Sync()

	// 3. Context — cancelled on SIGINT/SIGTERM for graceful shutdown
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// 4.1. Postgres
	pool, err := database.Connect(ctx, cfg.DbUri)
	if err != nil {
		logger.Log.Fatal("database connect failed", zap.Error(err))
	}
	defer pool.Close()

	// 4.3. RabbitMQ
	rmq, err := mq.NewRabbitMQ(cfg.MqUri)
	if err != nil {
		logger.Log.Fatal("rabbitmq connect failed", zap.Error(err))
	}
	defer func(rmq *mq.RabbitMQ) {
		err := rmq.Close()
		if err != nil {
			logger.Log.Fatal("rabbitmq close failed", zap.Error(err))
		}
	}(rmq)

	ch, err := rmq.Channel()
	if err != nil {
		logger.Log.Fatal("rabbitmq: open channel failed", zap.Error(err))
	}
	if err := mq.SetupTopology(ch); err != nil {
		logger.Log.Fatal("rabbitmq: setup topology failed", zap.Error(err))
	}
	err = ch.Close()
	if err != nil {
		logger.Log.Fatal("rabbitmq: close channel failed", zap.Error(err))
		return
	}

	// 8. HTTP server
	r := gin.Default()

	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
	})

	// 9. Start server
	logger.Log.Info("starting http server", zap.String("port", cfg.ServerPort))
	if err := r.Run(":" + cfg.ServerPort); err != nil {
		logger.Log.Fatal("server failed", zap.Error(err))
	}
}

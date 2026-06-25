package main

import (
	"context"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/gin-gonic/gin"
	queries "github.com/thinh0704hcm/zalord/backend/user-service/db/sqlc"
	docs "github.com/thinh0704hcm/zalord/backend/user-service/docs"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/database"
	grpcserver "github.com/thinh0704hcm/zalord/backend/user-service/internal/grpc"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/handler"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/repository"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/service"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/config"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	userv1 "github.com/thinh0704hcm/zalord/backend/user-service/proto/user/v1"
	"go.uber.org/zap"
	"google.golang.org/grpc"
)

// @title           User Service API
// @version         v1
// @description     Profile management for the Zalord chat system. Endpoints are reached through the Kong gateway (http://localhost:8080); Kong validates the JWT and injects the X-User-Id header that the service reads.
// @BasePath        /
// @securityDefinitions.apikey  BearerAuth
// @in                          header
// @name                        Authorization
// @description                 Paste the access token only — Swagger adds the "Bearer " prefix.
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

	// Wiring
	query := queries.New(pool)
	profileRepo := repository.NewProfileRepository(query)
	profileService := service.NewProfileService(profileRepo)
	profileHandler := handler.NewProfileHandler(profileService)

	// ── gRPC server (internal API — auth-service → CreateProfile) ──────────
	// Sync replacement for the old user.created event. Listens on a separate
	// port from HTTP (default 9082) so it's not routed through Kong.
	grpcPort := os.Getenv("GRPC_PORT")
	if grpcPort == "" {
		grpcPort = "9082"
	}
	go func() {
		lis, err := net.Listen("tcp", ":"+grpcPort)
		if err != nil {
			logger.Log.Fatal("grpc listen failed", zap.Error(err))
		}
		s := grpc.NewServer()
		userv1.RegisterUserInternalServer(s, grpcserver.NewUserServer(profileService))
		logger.Log.Info("grpc server listening", zap.String("port", grpcPort))
		if err := s.Serve(lis); err != nil {
			logger.Log.Fatal("grpc serve failed", zap.Error(err))
		}
	}()

	// HTTP
	r := gin.Default()
	r.GET("/health", func(c *gin.Context) { c.JSON(http.StatusOK, gin.H{"status": "ok"}) })

	// OpenAPI spec (raw JSON). Public — the aggregated Swagger UI fetches it
	// through Kong (/api-docs/user). Same-origin via Kong → no CORS.
	docs.SwaggerInfo.BasePath = "/"
	r.GET("/v3/api-docs", func(c *gin.Context) {
		c.Data(http.StatusOK, "application/json", []byte(docs.SwaggerInfo.ReadDoc()))
	})

	api := r.Group("/api/v1/users")
	api.Use(middleware.Identity())
	{
		api.GET("/me", profileHandler.GetMe)
		api.PUT("/me", profileHandler.UpdateMe)
		api.GET("/by-phone/:phone", profileHandler.GetByPhone)
		api.GET("/search", profileHandler.SearchByName)
		api.GET("/:userId", profileHandler.GetByUserID)
		api.GET("", middleware.RequireRole("ADMIN"), profileHandler.List)
	}

	logger.Log.Info("starting http server", zap.String("port", cfg.ServerPort))
	if err := r.Run(":" + cfg.ServerPort); err != nil {
		logger.Log.Fatal("server failed", zap.Error(err))
	}
}

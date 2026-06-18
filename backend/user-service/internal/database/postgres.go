package database

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	"go.uber.org/zap"
)

func Connect(ctx context.Context, dsn string) (*pgxpool.Pool, error) {
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		logger.Log.Error("creating pgx pool failed", zap.Error(err))
		return nil, err
	}

	if err := pool.Ping(ctx); err != nil {
		logger.Log.Error("ping pgx pool failed", zap.Error(err))
		return nil, err
	}

	return pool, nil
}

package redisx

import (
	"context"
	"os"
	"time"

	"github.com/redis/go-redis/extra/redisotel/v9"
	"github.com/redis/go-redis/v9"
)

// New builds a Redis client from REDIS_HOST/REDIS_PORT env. Pings to fail
// fast at startup rather than on the first hot-path operation.
func New(ctx context.Context) (*redis.Client, error) {
	host := os.Getenv("REDIS_HOST")
	if host == "" {
		host = "redis"
	}
	port := os.Getenv("REDIS_PORT")
	if port == "" {
		port = "6379"
	}
	cli := redis.NewClient(&redis.Options{
		Addr:         host + ":" + port,
		DialTimeout:  3 * time.Second,
		ReadTimeout:  2 * time.Second,
		WriteTimeout: 2 * time.Second,
	})
	pctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if err := cli.Ping(pctx).Err(); err != nil {
		_ = cli.Close()
		return nil, err
	}

	// Auto-instrument Redis calls with OTel spans.
	if err := redisotel.InstrumentTracing(cli); err != nil {
		return nil, err
	}

	return cli, nil
}

package eventbus

import (
	"context"
	"errors"
	"strings"
	"sync"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/logger"
	"go.uber.org/zap"
)

type kafkaConsumer struct {
	brokers []string
	readers []*kafka.Reader
	mu      sync.Mutex
}

func newKafkaConsumer(bootstrap string) (Consumer, error) {
	return &kafkaConsumer{brokers: strings.Split(bootstrap, ",")}, nil
}

func (k *kafkaConsumer) Subscribe(ctx context.Context, eventName, consumerGroup string, handler HandlerFunc) error {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:        k.brokers,
		Topic:          eventName,
		GroupID:        consumerGroup,
		MinBytes:       1,
		MaxBytes:       10 << 20,
		MaxWait:        1 * time.Second,
		CommitInterval: 0,
	})
	k.mu.Lock()
	k.readers = append(k.readers, reader)
	k.mu.Unlock()

	logger.Log.Info("kafka subscribed",
		zap.String("topic", eventName), zap.String("group", consumerGroup))

	go func() {
		for {
			if ctx.Err() != nil {
				return
			}
			msg, err := reader.FetchMessage(ctx)
			if err != nil {
				if ctx.Err() != nil {
					return
				}
				logger.Log.Warn("kafka fetch error", zap.Error(err))
				time.Sleep(1 * time.Second)
				continue
			}
			err = handler(ctx, msg.Value)
			if err == nil {
				if cerr := reader.CommitMessages(ctx, msg); cerr != nil {
					logger.Log.Warn("kafka commit failed", zap.Error(cerr))
				}
				continue
			}
			var perm *PermanentError
			if errors.As(err, &perm) {
				logger.Log.Warn("permanent error, dropping", zap.Error(err))
				_ = reader.CommitMessages(ctx, msg)
			} else {
				logger.Log.Warn("transient error, will re-poll", zap.Error(err))
				time.Sleep(500 * time.Millisecond)
			}
		}
	}()
	return nil
}

func (k *kafkaConsumer) Close() error {
	k.mu.Lock()
	defer k.mu.Unlock()
	var first error
	for _, r := range k.readers {
		if err := r.Close(); err != nil && first == nil {
			first = err
		}
	}
	return first
}

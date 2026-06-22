package mq

import (
	"context"
	"errors"

	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"go.uber.org/zap"
)

type HandlerFunc func(ctx context.Context, body []byte) error

type Consumer struct {
	rmq *RabbitMQ
}

func NewConsumer(rmq *RabbitMQ) *Consumer {
	return &Consumer{rmq: rmq}
}

func (c *Consumer) Consume(ctx context.Context, queue string, handler HandlerFunc) error {
	ch, err := c.rmq.Channel()
	if err != nil {
		return err
	}
	if err := ch.Qos(1, 0, false); err != nil {
		return err
	}
	msgs, err := ch.Consume(queue, "", false, false, false, false, nil)
	if err != nil {
		return err
	}

	go func() {
		defer func() { _ = ch.Close() }()
		for {
			select {
			case <-ctx.Done():
				return
			case msg, ok := <-msgs:
				if !ok {
					return
				}
				c.dispatch(ctx, handler, msg)
			}
		}
	}()
	return nil
}

func (c *Consumer) dispatch(ctx context.Context, handler HandlerFunc, msg amqp.Delivery) {
	err := handler(ctx, msg.Body)
	if err == nil {
		_ = msg.Ack(false)
		return
	}
	var perm *PermanentError
	if errors.As(err, &perm) {
		logger.Log.Warn("permanent error, dropping", zap.Error(err))
		_ = msg.Ack(false)
		return
	}
	logger.Log.Warn("transient error, requeuing", zap.Error(err))
	_ = msg.Nack(false, true)
}

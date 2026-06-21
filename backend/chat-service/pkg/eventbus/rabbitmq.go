package eventbus

import (
	"context"
	"errors"
	"strings"

	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"go.uber.org/zap"
)

type rabbitConsumer struct {
	conn *amqp.Connection
	chs  []*amqp.Channel
}

func newRabbitConsumer(amqpURL string) (Consumer, error) {
	conn, err := amqp.Dial(amqpURL)
	if err != nil {
		return nil, err
	}
	return &rabbitConsumer{conn: conn}, nil
}

func (r *rabbitConsumer) Subscribe(ctx context.Context, eventName, consumerGroup string, handler HandlerFunc) error {
	ch, err := r.conn.Channel()
	if err != nil {
		return err
	}
	r.chs = append(r.chs, ch)

	exchange := exchangeFromEventName(eventName)
	queue := consumerGroup + ".queue"

	if err := ch.ExchangeDeclare(exchange, "topic", true, false, false, false, nil); err != nil {
		return err
	}
	if _, err := ch.QueueDeclare(queue, true, false, false, false, nil); err != nil {
		return err
	}
	if err := ch.QueueBind(queue, eventName, exchange, false, nil); err != nil {
		return err
	}
	if err := ch.Qos(1, 0, false); err != nil {
		return err
	}

	msgs, err := ch.Consume(queue, "", false, false, false, false, nil)
	if err != nil {
		return err
	}
	logger.Log.Info("rabbitmq subscribed",
		zap.String("event", eventName), zap.String("queue", queue))

	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case msg, ok := <-msgs:
				if !ok {
					return
				}
				err := handler(ctx, msg.Body)
				if err == nil {
					_ = msg.Ack(false)
					continue
				}
				var perm *PermanentError
				if errors.As(err, &perm) {
					logger.Log.Warn("permanent error, dropping", zap.Error(err))
					_ = msg.Ack(false)
				} else {
					logger.Log.Warn("transient error, requeuing", zap.Error(err))
					_ = msg.Nack(false, true)
				}
			}
		}
	}()
	return nil
}

func (r *rabbitConsumer) Close() error {
	for _, ch := range r.chs {
		_ = ch.Close()
	}
	return r.conn.Close()
}

func exchangeFromEventName(eventName string) string {
	i := strings.IndexByte(eventName, '.')
	if i < 0 {
		return eventName + ".exchange"
	}
	return eventName[:i] + ".exchange"
}

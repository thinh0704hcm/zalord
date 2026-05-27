package mq

import (
	"context"

	amqp "github.com/rabbitmq/amqp091-go"
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

	err = ch.Qos(1, 0, false)
	if err != nil {
		return err
	} // fair dispatch

	msgs, err := ch.Consume(queue, "", false, false, false, false, nil)
	if err != nil {
		return err
	}

	go func() {
		defer func(ch *amqp.Channel) {
			err := ch.Close()
			if err != nil {
				return
			}
		}(ch)
		for {
			select {
			case <-ctx.Done():
				return
			case msg, ok := <-msgs:
				if !ok {
					return
				}
				if err := handler(ctx, msg.Body); err != nil {
					err := msg.Nack(false, true)
					if err != nil {
						return
					} // requeue
					continue
				}
				err := msg.Ack(false)
				if err != nil {
					return
				}
			}
		}
	}()

	return nil
}

package mq

import (
	"fmt"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"

	"go.uber.org/zap"
)

type RabbitMQ struct {
	conn *amqp.Connection
	url  string
}

func NewRabbitMQ(url string) (*RabbitMQ, error) {
	r := &RabbitMQ{url: url}
	if err := r.connect(); err != nil {
		return nil, err
	}
	return r, nil
}

func (r *RabbitMQ) connect() error {
	conn, err := amqp.Dial(r.url)
	if err != nil {
		return fmt.Errorf("rabbitmq: dial failed: %w", err)
	}
	r.conn = conn

	// Watch for unexpected close and reconnect
	go r.watchConnection()
	return nil
}

func (r *RabbitMQ) watchConnection() {
	_, ok := <-r.conn.NotifyClose(make(chan *amqp.Error, 1))
	if !ok {
		return // graceful close, no reconnect needed
	}
	r.reconnect()
}

func (r *RabbitMQ) reconnect() {
	for {
		time.Sleep(5 * time.Second)
		logger.Log.Error("reconnecting to RabbitMQ")
		if err := r.connect(); err != nil {
			logger.Log.Error("reconnecting to RabbitMQ failed", zap.Error(err))
			continue
		}
		return
	}
}

func (r *RabbitMQ) Channel() (*amqp.Channel, error) {
	return r.conn.Channel()
}

func (r *RabbitMQ) Close() error {
	return r.conn.Close()
}

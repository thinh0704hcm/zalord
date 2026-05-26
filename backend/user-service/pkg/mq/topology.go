package mq

import (
	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	"go.uber.org/zap"
)

var (
	UserQueue      = "user.queue"
	UserExchange   = "user.exchange"
	UserBindingKey = "user.#"
)

func SetupTopology(ch *amqp.Channel) error {
	// Single topic exchange for all app events
	if err := ch.ExchangeDeclare(
		UserExchange, "topic", true, false, false, false, nil,
	); err != nil {
		logger.Log.Error("declare exchange failed", zap.Error(err))
		return err
	}

	// Queue: user notifications
	if _, err := ch.QueueDeclare(
		UserQueue, true, false, false, false, nil,
	); err != nil {
		logger.Log.Error("declare queue failed", zap.Error(err))
		return err
	}

	if err := ch.QueueBind(
		UserQueue, UserBindingKey, UserExchange, false, nil,
	); err != nil {
		logger.Log.Error("bind queue failed", zap.Error(err))
		return err
	}

	return nil
}

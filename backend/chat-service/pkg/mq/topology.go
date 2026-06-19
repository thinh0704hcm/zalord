package mq

import (
	amqp "github.com/rabbitmq/amqp091-go"
)

// Names MUST match the producer side (message-service RabbitMQConfig.java).
// Exchange is shared; chat-service declares its OWN queue + binding so it
// gets a copy of every message.created event independently of other consumers
// (InboxProjector, future search-indexer, etc).
const (
	MessageExchange   = "message.exchange"
	ChatDeliveryQueue = "chat.delivery.queue"
	ChatBindingKey    = "message.created"
)

func SetupTopology(ch *amqp.Channel) error {
	if err := ch.ExchangeDeclare(
		MessageExchange, "topic", true, false, false, false, nil,
	); err != nil {
		return err
	}
	if _, err := ch.QueueDeclare(
		ChatDeliveryQueue, true, false, false, false, nil,
	); err != nil {
		return err
	}
	return ch.QueueBind(ChatDeliveryQueue, ChatBindingKey, MessageExchange, false, nil)
}

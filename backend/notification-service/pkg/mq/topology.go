package mq

import amqp "github.com/rabbitmq/amqp091-go"

// We're a consumer for both message.exchange and group.exchange. Each gets
// its own queue so the two streams can be drained independently and so a
// stuck event in one stream doesn't head-of-line block the other.
const (
	MessageExchange = "message.exchange"
	GroupExchange   = "group.exchange"

	MessageQueue = "notification.message.queue"
	GroupQueue   = "notification.group.queue"

	MessageBindingKey = "message.created"
	GroupBindingKey   = "group.#"
)

func SetupTopology(ch *amqp.Channel) error {
	// Exchanges (idempotent — match upstream producers).
	if err := ch.ExchangeDeclare(MessageExchange, "topic", true, false, false, false, nil); err != nil {
		return err
	}
	if err := ch.ExchangeDeclare(GroupExchange, "topic", true, false, false, false, nil); err != nil {
		return err
	}

	// Message queue + binding.
	if _, err := ch.QueueDeclare(MessageQueue, true, false, false, false, nil); err != nil {
		return err
	}
	if err := ch.QueueBind(MessageQueue, MessageBindingKey, MessageExchange, false, nil); err != nil {
		return err
	}

	// Group queue + binding.
	if _, err := ch.QueueDeclare(GroupQueue, true, false, false, false, nil); err != nil {
		return err
	}
	if err := ch.QueueBind(GroupQueue, GroupBindingKey, GroupExchange, false, nil); err != nil {
		return err
	}
	return nil
}

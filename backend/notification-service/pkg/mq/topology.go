package mq

import amqp "github.com/rabbitmq/amqp091-go"

// Topology constants. After the EventBus refactor, the message.created path
// is owned by pkg/eventbus (which declares its own queue+binding). Only the
// group.* path still uses this raw mq package.
const (
	MessageExchange = "message.exchange" // kept for back-compat / future raw use
	GroupExchange   = "group.exchange"

	MessageQueue = "notification.message.queue" // legacy — no longer subscribed
	GroupQueue   = "notification.group.queue"

	MessageBindingKey = "message.created"
	GroupBindingKey   = "group.#"
)

// SetupGroupTopology declares only the group.* path (the part still using
// raw RabbitMQ). The message.created queue/binding is created by the
// eventbus.rabbitConsumer at subscribe time.
func SetupGroupTopology(ch *amqp.Channel) error {
	if err := ch.ExchangeDeclare(GroupExchange, "topic", true, false, false, false, nil); err != nil {
		return err
	}
	if _, err := ch.QueueDeclare(GroupQueue, true, false, false, false, nil); err != nil {
		return err
	}
	return ch.QueueBind(GroupQueue, GroupBindingKey, GroupExchange, false, nil)
}

// SetupTopology kept for back-compat; equivalent to SetupGroupTopology now.
func SetupTopology(ch *amqp.Channel) error { return SetupGroupTopology(ch) }

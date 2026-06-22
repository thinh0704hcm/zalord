package zalord.message_service.eventbus;

/**
 * Pluggable async-event publisher. Two backends selected by zalord.event-bus:
 *   - "rabbitmq" → {@link RabbitMQEventPublisher} (default)
 *   - "kafka"    → {@link KafkaEventPublisher}
 *
 * Used for message.created today. group.* events still go via raw RabbitMQ
 * because the cross-broker benchmark only covers the high-volume message path.
 *
 * eventName convention: "message.created"
 *   Rabbit: prefix → exchange ("message.exchange"), full → routing key
 *   Kafka:  full  → topic
 */
public interface EventPublisher {
    void publish(String eventName, byte[] payload);
}

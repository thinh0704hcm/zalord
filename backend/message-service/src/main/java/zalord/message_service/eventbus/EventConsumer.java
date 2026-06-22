package zalord.message_service.eventbus;

/**
 * Pluggable async-event consumer. Two implementations registered via
 * @ConditionalOnProperty (only one active at a time):
 *   - {@link RabbitMQEventConsumer} (default)
 *   - {@link KafkaEventConsumer}
 *
 * Used here for the message.created path (InboxProjector). Other events
 * (group.*) still use raw @RabbitListener — abstraction scope is bounded
 * to the chain we benchmark.
 */
public interface EventConsumer {
    /**
     * Subscribe to an event. The handler receives raw bytes (the published
     * payload). Implementations spawn their own threads / containers.
     *
     * @param eventName     e.g. "message.created"
     * @param consumerGroup logical group id — RabbitMQ uses it as queue name,
     *                      Kafka as group.id
     * @param handler       business callback; throw to NACK (rabbit) / re-poll (kafka)
     */
    void subscribe(String eventName, String consumerGroup, EventHandler handler);

    @FunctionalInterface
    interface EventHandler {
        void handle(byte[] payload) throws Exception;
    }
}

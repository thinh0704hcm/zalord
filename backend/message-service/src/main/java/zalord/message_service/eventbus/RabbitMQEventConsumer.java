package zalord.message_service.eventbus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "zalord.event-bus", havingValue = "rabbitmq", matchIfMissing = true)
@Slf4j
public class RabbitMQEventConsumer implements EventConsumer {

    private final ConnectionFactory connectionFactory;
    private final RabbitAdmin rabbitAdmin;

    public RabbitMQEventConsumer(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        this.rabbitAdmin = new RabbitAdmin(connectionFactory);
        log.info("EventConsumer backend = RABBITMQ");
    }

    @Override
    public void subscribe(String eventName, String consumerGroup, EventHandler handler) {
        String exchangeName = exchangeFromEventName(eventName);
        String queueName = consumerGroup + ".queue";

        // Declare topology idempotently.
        TopicExchange exchange = new TopicExchange(exchangeName, true, false);
        Queue queue = QueueBuilder.durable(queueName).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(eventName);
        rabbitAdmin.declareExchange(exchange);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(binding);

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setAcknowledgeMode(AcknowledgeMode.AUTO);     // ack if handler returns normally
        container.setPrefetchCount(1);
        container.setMessageListener(msg -> {
            try {
                handler.handle(msg.getBody());
            } catch (Exception e) {
                // Throwing here makes AUTO mode requeue. Permanent errors
                // should be swallowed by the handler (log + return).
                throw new RuntimeException(e);
            }
        });
        container.start();
        log.info("rabbitmq subscribed event={} queue={}", eventName, queueName);
    }

    private static String exchangeFromEventName(String eventName) {
        int dot = eventName.indexOf('.');
        return (dot > 0 ? eventName.substring(0, dot) : eventName) + ".exchange";
    }
}

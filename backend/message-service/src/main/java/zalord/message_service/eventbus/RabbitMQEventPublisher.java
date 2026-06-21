package zalord.message_service.eventbus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "zalord.event-bus", havingValue = "rabbitmq", matchIfMissing = true)
@Slf4j
public class RabbitMQEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        log.info("EventPublisher backend = RABBITMQ");
    }

    @Override
    public void publish(String eventName, byte[] payload) {
        String exchange = exchangeFromEventName(eventName);
        Message msg = MessageBuilder
                .withBody(payload)
                .andProperties(MessagePropertiesBuilder.newInstance()
                        .setContentType("application/json")
                        .setContentEncoding("UTF-8")
                        .build())
                .build();
        rabbitTemplate.send(exchange, eventName, msg);
    }

    private static String exchangeFromEventName(String eventName) {
        int dot = eventName.indexOf('.');
        String prefix = dot > 0 ? eventName.substring(0, dot) : eventName;
        return prefix + ".exchange";
    }
}

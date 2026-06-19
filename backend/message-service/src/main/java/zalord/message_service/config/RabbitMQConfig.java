package zalord.message_service.config;

import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange for message events. Topic so consumers can subscribe to
    // patterns like message.# (everything) or message.created (just creates).
    public static final String MESSAGE_EXCHANGE = "message.exchange";

    // Routing key for "a message was created" events.
    public static final String MESSAGE_CREATED_ROUTING_KEY = "message.created";

    // Smoke-test queue — chat-service (Sprint Core-2) will declare its own.
    public static final String MESSAGE_QUEUE = "message.queue";
    public static final String MESSAGE_BINDING_KEY = "message.#";

    // CQRS inbox projector queue — message-service consumes its OWN
    // message.created events to update the conversation_views read model.
    // This is what makes it CQRS: write side (POST /messages) and read side
    // (GET /inbox) are separated by an event boundary, even though they live
    // in the same service for now.
    public static final String INBOX_PROJECTOR_QUEUE = "message.inbox.projector.queue";

    // group-service publishes group.* events here. message-service projects
    // a Conversation (id = groupId) and its ConversationMember rows so the
    // existing message-send / inbox flow works transparently for groups.
    public static final String GROUP_EXCHANGE = "group.exchange";
    public static final String GROUP_EVENTS_QUEUE = "message.group.events.queue";
    public static final String GROUP_BINDING_KEY = "group.#";

    @Bean
    public TopicExchange messageExchange() {
        return new TopicExchange(MESSAGE_EXCHANGE, true, false);
    }

    @Bean
    public Queue messageQueue() {
        return QueueBuilder.durable(MESSAGE_QUEUE).build();
    }

    @Bean
    public Binding messageBinding(Queue messageQueue, TopicExchange messageExchange) {
        return BindingBuilder.bind(messageQueue).to(messageExchange).with(MESSAGE_BINDING_KEY);
    }

    @Bean
    public Queue inboxProjectorQueue() {
        return QueueBuilder.durable(INBOX_PROJECTOR_QUEUE).build();
    }

    @Bean
    public Binding inboxProjectorBinding(Queue inboxProjectorQueue, TopicExchange messageExchange) {
        return BindingBuilder.bind(inboxProjectorQueue).to(messageExchange).with(MESSAGE_CREATED_ROUTING_KEY);
    }

    @Bean
    public TopicExchange groupExchange() {
        return new TopicExchange(GROUP_EXCHANGE, true, false);
    }

    @Bean
    public Queue groupEventsQueue() {
        return QueueBuilder.durable(GROUP_EVENTS_QUEUE).build();
    }

    @Bean
    public Binding groupEventsBinding(Queue groupEventsQueue, TopicExchange groupExchange) {
        return BindingBuilder.bind(groupEventsQueue).to(groupExchange).with(GROUP_BINDING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}

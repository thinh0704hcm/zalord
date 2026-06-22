package zalord.group_service.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String GROUP_EXCHANGE = "group.exchange";

    public static final String GROUP_CREATED_ROUTING_KEY        = "group.created";
    public static final String GROUP_MEMBER_ADDED_ROUTING_KEY   = "group.member.added";
    public static final String GROUP_MEMBER_REMOVED_ROUTING_KEY = "group.member.removed";
    public static final String GROUP_UPDATED_ROUTING_KEY        = "group.updated";

    @Bean
    public TopicExchange groupExchange() {
        return new TopicExchange(GROUP_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate t = new RabbitTemplate(connectionFactory);
        t.setMessageConverter(messageConverter);
        return t;
    }
}

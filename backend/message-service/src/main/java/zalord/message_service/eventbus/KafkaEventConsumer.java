package zalord.message_service.eventbus;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "zalord.event-bus", havingValue = "kafka")
@Slf4j
public class KafkaEventConsumer implements EventConsumer {

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrap;

    public KafkaEventConsumer() {
        log.info("EventConsumer backend = KAFKA");
    }

    @Override
    public void subscribe(String eventName, String consumerGroup, EventHandler handler) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        DefaultKafkaConsumerFactory<String, byte[]> factory = new DefaultKafkaConsumerFactory<>(props);
        ContainerProperties containerProps = new ContainerProperties(eventName);
        containerProps.setGroupId(consumerGroup);
        containerProps.setMessageListener((MessageListener<String, byte[]>) record -> {
            try {
                handler.handle(record.value());
            } catch (Exception e) {
                // Spring Kafka treats exceptions as failure → won't commit
                // offset → message redelivered on next poll. Same semantics
                // as RabbitMQ requeue.
                throw new RuntimeException(e);
            }
        });

        ConcurrentMessageListenerContainer<String, byte[]> container =
                new ConcurrentMessageListenerContainer<>(factory, containerProps);
        container.setConcurrency(1);
        container.start();
        log.info("kafka subscribed topic={} group={}", eventName, consumerGroup);
    }
}

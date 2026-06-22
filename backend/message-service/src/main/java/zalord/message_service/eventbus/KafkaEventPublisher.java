package zalord.message_service.eventbus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "zalord.event-bus", havingValue = "kafka")
@Slf4j
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        log.info("EventPublisher backend = KAFKA");
    }

    @Override
    public void publish(String eventName, byte[] payload) {
        // Topic == eventName; partitioner picks a partition (no key set).
        // Topics pre-created by infra/kafka/create-topics.sh.
        kafkaTemplate.send(eventName, payload);
    }
}

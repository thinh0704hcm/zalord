package zalord.auth_service.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zalord.auth_service.model.OutboxEvent;
import zalord.auth_service.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class OutboxScheduler {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxRepo;
    private final RabbitTemplate rabbitTemplate;

    public OutboxScheduler(OutboxEventRepository outboxRepo, RabbitTemplate rabbitTemplate) {
        this.outboxRepo = outboxRepo;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepo.lockUnpublishedBatch(BATCH_SIZE);
        if (batch.isEmpty()) return;

        log.info("Outbox: picked up {} pending event(s)", batch.size());
        Instant now = Instant.now();
        int published = 0;
        int failed = 0;
        for (OutboxEvent e : batch) {
            try {
                Message msg = MessageBuilder
                        .withBody(e.getPayload().getBytes(StandardCharsets.UTF_8))
                        .andProperties(MessagePropertiesBuilder.newInstance()
                                .setContentType("application/json")
                                .setContentEncoding("UTF-8")
                                .build())
                        .build();

                rabbitTemplate.send(e.getTopicExchange(), e.getRoutingKey(), msg);
                e.setPublishedAt(now);
                published++;
                log.debug("Published outbox event id={} exchange={} routingKey={}",
                        e.getId(), e.getTopicExchange(), e.getRoutingKey());
            } catch (AmqpException ex) {
                failed++;
                log.error("Failed to publish outbox event id={} routingKey={}: {}",
                        e.getId(), e.getRoutingKey(), ex.getMessage());
            }
        }
        log.info("Outbox: batch done published={} failed={}", published, failed);
    }
}

package zalord.message_service.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zalord.message_service.model.OutboxEvent;
import zalord.message_service.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox table every 3s for unpublished rows and ships them to
 * RabbitMQ. Each row is locked with FOR UPDATE SKIP LOCKED so multiple
 * instances of the service can run safely without double-publishing.
 */
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
            } catch (AmqpException ex) {
                failed++;
                log.error("Outbox publish failed id={}: {}", e.getId(), ex.getMessage());
            }
        }
        log.info("Outbox: batch done published={} failed={}", published, failed);
    }
}

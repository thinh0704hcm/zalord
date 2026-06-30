package zalord.message_service.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zalord.message_service.eventbus.EventPublisher;
import zalord.message_service.model.OutboxEvent;
import zalord.message_service.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Polls outbox table on a fixed delay and ships pending events via the
 * EventPublisher abstraction — backend (RabbitMQ or Kafka) is chosen at startup
 * by the zalord.event-bus property. FOR UPDATE SKIP LOCKED enables multi-instance.
 *
 * Poll interval defaults to 3000ms; OUTBOX_POLL_MS env overrides it (benchmark
 * sets it to 50ms so end-to-end latency reflects broker perf, not poll wait).
 */
@Component
@Slf4j
public class OutboxScheduler {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxRepo;
    private final EventPublisher eventBus;

    public OutboxScheduler(OutboxEventRepository outboxRepo, EventPublisher eventBus) {
        this.outboxRepo = outboxRepo;
        this.eventBus = eventBus;
    }

    @Scheduled(fixedDelayString = "${zalord.outbox-poll-ms:3000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepo.lockUnpublishedBatch(BATCH_SIZE);
        if (batch.isEmpty()) return;

        Instant now = Instant.now();
        int published = 0;
        int failed = 0;
        for (OutboxEvent e : batch) {
            try {
                // routingKey is the canonical event name ("message.created");
                // EventPublisher impl maps it to RabbitMQ exchange or Kafka topic.
                eventBus.publish(e.getRoutingKey(), e.getPayload().getBytes(StandardCharsets.UTF_8));
                e.setPublishedAt(now);
                published++;
            } catch (Exception ex) {
                failed++;
                log.error("Outbox publish failed id={} eventName={}: {}",
                        e.getId(), e.getRoutingKey(), ex.getMessage());
            }
        }
        log.info("Outbox: batch done published={} failed={}", published, failed);
    }
}

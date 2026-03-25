package io.zalord.messaging.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import io.zalord.messaging.domain.entities.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    Optional<Message> findFirstByChatIdOrderByCreatedAtDesc(UUID chatId);
    Slice<Message> findByChatIdOrderByCreatedAtDesc(UUID chatId, Pageable pageable);
    Slice<Message> findByChatIdAndCreatedAtBeforeOrderByCreatedAtDesc(
        UUID chatId,
        Instant before,
        Pageable pageable
    );
}
package zalord.message_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zalord.message_service.model.Message;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Page<Message> findByConversationIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(UUID conversationId, java.time.Instant createdAt, Pageable pageable);

    /** Used by /inbox/{id}/read when client doesn't pass a specific messageId. */
    java.util.Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /**
     * After a recall, the InboxProjector calls this to find the next message
     * that should drive the inbox preview. Skips recalled rows so the preview
     * shows the most recent NON-recalled message.
     */
    java.util.Optional<Message> findFirstByConversationIdAndRecalledAtIsNullOrderByCreatedAtDesc(UUID conversationId);
}

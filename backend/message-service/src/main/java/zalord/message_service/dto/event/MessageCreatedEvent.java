package zalord.message_service.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published to the message.exchange topic with routing key message.created
 * whenever a message is persisted. recipientIds = all conversation members
 * minus the sender — chat-service uses this to fan out via WebSocket.
 */
public record MessageCreatedEvent(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        List<UUID> recipientIds,
        String content,
        Instant createdAt
) {
}

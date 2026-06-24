package zalord.message_service.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the message.exchange topic with routing key message.read
 * when a user marks a conversation as read. chat-service consumes this and
 * pushes "Seen" markers over WebSocket to every member of the conversation.
 */
public record MessageReadEvent(
        UUID conversationId,
        UUID readerId,
        UUID lastReadMessageId,
        Instant readAt
) {
}

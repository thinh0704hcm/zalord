package zalord.message_service.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published to the message.exchange topic with routing key message.created
 * whenever a message is persisted. recipientIds = all conversation members
 * minus the sender — chat-service uses this to fan out via WebSocket.
 * attachmentIds is empty for plain-text messages; non-empty when the sender
 * attached files/images/videos (already validated against media-service).
 */
public record MessageCreatedEvent(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        List<UUID> recipientIds,
        String content,
        List<UUID> attachmentIds,
        Instant createdAt,
        // Reply snapshot — null for non-replies. Embedded in the event so
        // chat-service can render the quote without an extra DB round trip.
        ReplyToSnippet replyTo
) {
    /** Inline snippet to keep one event = one wire payload. */
    public record ReplyToSnippet(
            UUID messageId,
            UUID senderId,
            String preview
    ) {
    }
}

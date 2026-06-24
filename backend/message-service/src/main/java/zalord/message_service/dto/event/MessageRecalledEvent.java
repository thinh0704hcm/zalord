package zalord.message_service.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the message.exchange topic with routing key message.recalled
 * when a user recalls ("thu hồi") one of their own messages. Consumers:
 *   - chat-service     → push WS frame so live clients blank the bubble
 *   - InboxProjector   → if this was the conv's last message, rewrite the
 *                         inbox preview to "Tin nhắn đã được thu hồi"
 *
 * Note: notification-service does NOT consume this — bell-icon notifications
 * are part of the user's history and intentionally outlive the underlying
 * message (you can't un-ring a bell that already rang on someone's phone).
 */
public record MessageRecalledEvent(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        Instant recalledAt
) {
}

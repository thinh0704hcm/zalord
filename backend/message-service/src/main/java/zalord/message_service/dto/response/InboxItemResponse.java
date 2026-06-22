package zalord.message_service.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record InboxItemResponse(
        UUID conversationId,
        UUID otherUserId,
        String lastMessagePreview,
        Instant lastMessageAt,
        UUID lastSenderId,
        int unreadCount
) {
}

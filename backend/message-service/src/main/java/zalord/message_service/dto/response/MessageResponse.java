package zalord.message_service.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        List<UUID> attachmentIds,
        Instant createdAt
) {
}

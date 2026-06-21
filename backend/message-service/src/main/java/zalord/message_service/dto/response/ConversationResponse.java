package zalord.message_service.dto.response;

import lombok.Builder;
import zalord.message_service.enums.ConversationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record ConversationResponse(
        UUID id,
        ConversationType type,
        List<UUID> memberIds,
        Instant createdAt
) {
}

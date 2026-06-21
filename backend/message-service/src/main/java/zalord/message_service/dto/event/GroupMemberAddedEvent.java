package zalord.message_service.dto.event;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberAddedEvent(
        UUID groupId,
        UUID userId,
        Instant joinedAt
) {
}

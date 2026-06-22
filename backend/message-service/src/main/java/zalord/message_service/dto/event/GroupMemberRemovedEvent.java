package zalord.message_service.dto.event;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberRemovedEvent(
        UUID groupId,
        UUID userId,
        Instant removedAt
) {
}

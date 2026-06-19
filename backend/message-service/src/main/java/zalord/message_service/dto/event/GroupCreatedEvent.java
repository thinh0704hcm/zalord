package zalord.message_service.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mirror of group-service's GroupCreatedEvent record. */
public record GroupCreatedEvent(
        UUID groupId,
        String name,
        UUID createdBy,
        List<UUID> memberIds,
        Instant createdAt
) {
}

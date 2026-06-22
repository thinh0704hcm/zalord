package zalord.group_service.dto.event;

import java.time.Instant;
import java.util.UUID;

/** Routing key: group.updated. Lets message-service update cached group name
 *  for inbox display (optional — message-service may also pull on demand). */
public record GroupUpdatedEvent(
        UUID groupId,
        String name,
        String avatarUrl,
        Instant updatedAt
) {
}

package zalord.group_service.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Routing key: group.created. Consumed by message-service to project a
 *  Conversation (id = groupId) with ConversationMembers and CQRS views. */
public record GroupCreatedEvent(
        UUID groupId,
        String name,
        UUID createdBy,
        List<UUID> memberIds,
        Instant createdAt
) {
}

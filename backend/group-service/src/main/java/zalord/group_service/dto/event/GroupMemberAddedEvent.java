package zalord.group_service.dto.event;

import java.time.Instant;
import java.util.UUID;

/** Routing key: group.member.added. message-service appends ConversationMember +
 *  inits the new user's ConversationView for this conv. */
public record GroupMemberAddedEvent(
        UUID groupId,
        UUID userId,
        Instant joinedAt
) {
}

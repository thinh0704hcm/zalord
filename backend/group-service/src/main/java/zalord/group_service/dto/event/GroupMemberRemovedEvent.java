package zalord.group_service.dto.event;

import java.time.Instant;
import java.util.UUID;

/** Routing key: group.member.removed. message-service deletes ConversationMember
 *  + (optionally) the user's ConversationView so the group disappears from
 *  their inbox. */
public record GroupMemberRemovedEvent(
        UUID groupId,
        UUID userId,
        Instant removedAt
) {
}

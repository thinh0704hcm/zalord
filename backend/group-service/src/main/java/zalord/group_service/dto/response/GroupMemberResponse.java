package zalord.group_service.dto.response;

import lombok.Builder;
import zalord.group_service.enums.MemberRole;

import java.time.Instant;
import java.util.UUID;

@Builder
public record GroupMemberResponse(
        UUID userId,
        MemberRole role,
        Instant joinedAt
) {
}

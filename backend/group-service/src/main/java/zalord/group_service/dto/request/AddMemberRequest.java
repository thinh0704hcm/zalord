package zalord.group_service.dto.request;

import jakarta.validation.constraints.NotNull;
import zalord.group_service.enums.MemberRole;

import java.util.UUID;

public record AddMemberRequest(
        @NotNull UUID userId,
        MemberRole role          // defaults to MEMBER if null
) {
}

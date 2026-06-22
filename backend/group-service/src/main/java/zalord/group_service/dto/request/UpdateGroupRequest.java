package zalord.group_service.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @Size(max = 100) String name,      // null = no change
        String avatarUrl                    // null = no change, empty string = clear
) {
}

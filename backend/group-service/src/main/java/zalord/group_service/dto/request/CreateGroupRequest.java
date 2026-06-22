package zalord.group_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateGroupRequest(
        @NotBlank @Size(max = 100) String name,
        String avatarUrl,
        @NotEmpty List<UUID> memberIds  // initial members (excluding the creator — caller is auto-added as OWNER)
) {
}

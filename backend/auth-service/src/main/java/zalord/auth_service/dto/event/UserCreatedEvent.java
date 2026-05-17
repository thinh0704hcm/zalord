package zalord.auth_service.dto.event;

import java.util.UUID;

public record UserCreatedEvent(
        UUID id,
        String displayName
) {
}

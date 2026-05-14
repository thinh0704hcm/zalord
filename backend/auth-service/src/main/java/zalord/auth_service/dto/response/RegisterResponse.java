package zalord.auth_service.dto.response;

import java.time.Instant;

public record RegisterResponse(
        String displayName,
        String phoneNumber,
        Instant createdAt
) {
}

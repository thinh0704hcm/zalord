package zalord.auth_service.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record RegisterResponse(
        String displayName,
        String phoneNumber,
        Instant createdAt
) {
}

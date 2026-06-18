package zalord.auth_service.dto.response;

import lombok.Builder;

@Builder
public record RefreshResponse(
        String accessToken
) {
}

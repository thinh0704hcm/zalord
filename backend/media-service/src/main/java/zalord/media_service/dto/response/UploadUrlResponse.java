package zalord.media_service.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UploadUrlResponse(
        UUID mediaId,
        String uploadUrl,        // presigned PUT URL — client uploads bytes here
        String httpMethod,        // "PUT"
        Instant expiresAt
) {
}

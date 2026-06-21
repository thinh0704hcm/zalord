package zalord.media_service.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record DownloadUrlResponse(
        String downloadUrl,      // presigned GET URL — browser fetches the bytes
        Instant expiresAt
) {
}

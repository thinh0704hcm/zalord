package zalord.media_service.dto.response;

import lombok.Builder;
import zalord.media_service.enums.MediaKind;
import zalord.media_service.enums.MediaStatus;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MediaResponse(
        UUID id,
        UUID ownerId,
        MediaKind kind,
        UUID conversationId,
        String mimeType,
        Long sizeBytes,
        MediaStatus status,
        Instant createdAt,
        Instant finalizedAt
) {
}

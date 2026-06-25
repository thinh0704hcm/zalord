package zalord.media_service.dto.request;

import jakarta.validation.constraints.NotNull;
import zalord.media_service.enums.MediaKind;

import java.util.UUID;

public record UploadUrlRequest(
        @NotNull MediaKind kind,
        /** Required for ATTACHMENT (caller must be a member). Ignored for AVATAR. */
        UUID conversationId,
        /** Optional — used by MinIO to set Content-Type on the object. */
        String mimeType,
        String fileName
) {
}

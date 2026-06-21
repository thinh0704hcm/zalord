package zalord.message_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * content OR attachmentIds (or both) must be non-empty — the service enforces
 * this. attachmentIds is capped at 10 to keep the validation gRPC round small.
 */
public record SendMessageRequest(
        @NotNull UUID conversationId,
        @Size(max = 4000) String content,
        @Size(max = 10) List<UUID> attachmentIds
) {
}

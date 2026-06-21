package zalord.message_service.dto.request;

import jakarta.validation.constraints.NotNull;
import zalord.message_service.enums.ConversationType;

import java.util.UUID;

public record CreateConversationRequest(
        @NotNull ConversationType type,
        // For DIRECT: the other user's id. (GROUP support comes later.)
        @NotNull UUID memberUserId
) {
}

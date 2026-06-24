package zalord.message_service.dto.request;

import java.util.UUID;

/**
 * Optional body for POST /api/v1/inbox/{conversationId}/read.
 * If messageId is null, the service uses the latest message in the conversation.
 */
public record MarkReadRequest(
        UUID messageId
) {
}

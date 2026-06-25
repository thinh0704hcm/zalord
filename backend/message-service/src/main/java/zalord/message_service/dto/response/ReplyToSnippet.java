package zalord.message_service.dto.response;

import lombok.Builder;

import java.util.UUID;

/**
 * Denormalized snippet of a quoted message, captured at the time of reply.
 * Kept stable even if the original message is later recalled — clients render
 * "(Tin nhắn đã được thu hồi)" by separately checking if `messageId` resolves
 * to a recalled row in their local cache, not by mutating this snippet.
 */
@Builder
public record ReplyToSnippet(
        UUID messageId,
        UUID senderId,
        String preview
) {
}

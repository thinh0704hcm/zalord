package zalord.message_service.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record MessageResponse(
		UUID id,
		UUID conversationId,
		UUID senderId,
		String content,
		List<UUID> attachmentIds,
		Instant createdAt,
		// Recall: when non-null, content + attachmentIds are blanked; clients
		// render "Tin nhắn đã được thu hồi" placeholder.
		Instant recalledAt,
		// Reply: snapshot of the quoted message (null for non-replies).
		ReplyToSnippet replyTo
	) {
		public String getContent() { return content(); }
		public UUID getSenderId() { return senderId(); }
	}
}

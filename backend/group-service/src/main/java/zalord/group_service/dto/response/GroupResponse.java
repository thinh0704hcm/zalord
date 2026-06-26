package zalord.group_service.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record GroupResponse(
		UUID id,                       // also the conversationId in message-service
		String name,
		String avatarUrl,
		UUID createdBy,
		Instant createdAt,
		List<GroupMemberResponse> members
	) {
		public String getName() { return name(); }
		public List<GroupMemberResponse> getMembers() { return members(); }
	
}

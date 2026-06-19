package zalord.message_service.service;

import zalord.message_service.dto.request.CreateConversationRequest;
import zalord.message_service.dto.response.ConversationResponse;
import zalord.message_service.dto.response.PageResponse;

import java.util.UUID;

public interface IConversationService {

    ConversationResponse create(UUID callerUserId, CreateConversationRequest request);

    ConversationResponse get(UUID callerUserId, UUID conversationId);

    PageResponse<ConversationResponse> listMine(UUID callerUserId, int page, int size);
}

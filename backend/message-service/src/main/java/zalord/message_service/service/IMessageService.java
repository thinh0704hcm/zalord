package zalord.message_service.service;

import zalord.message_service.dto.request.SendMessageRequest;
import zalord.message_service.dto.response.MessageResponse;
import zalord.message_service.dto.response.PageResponse;

import java.util.UUID;

public interface IMessageService {

    MessageResponse send(UUID callerUserId, SendMessageRequest request);

    PageResponse<MessageResponse> history(UUID callerUserId, UUID conversationId, int page, int size);
}

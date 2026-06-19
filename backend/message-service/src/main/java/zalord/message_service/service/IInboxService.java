package zalord.message_service.service;

import zalord.message_service.dto.response.InboxItemResponse;
import zalord.message_service.dto.response.PageResponse;

import java.util.UUID;

public interface IInboxService {

    PageResponse<InboxItemResponse> listInbox(UUID userId, int page, int size);

    void markRead(UUID userId, UUID conversationId);
}

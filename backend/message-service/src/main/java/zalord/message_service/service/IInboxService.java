package zalord.message_service.service;

import zalord.message_service.dto.response.InboxItemResponse;
import zalord.message_service.dto.response.PageResponse;

import java.util.UUID;

public interface IInboxService {

    PageResponse<InboxItemResponse> listInbox(UUID userId, int page, int size);

    /**
     * Mark a conversation as read up to (and including) messageId. If messageId
     * is null, the latest message in the conversation is used. Publishes
     * message.read so other members' UIs update their "Seen" markers.
     */
    void markRead(UUID userId, UUID conversationId, UUID messageId);
}

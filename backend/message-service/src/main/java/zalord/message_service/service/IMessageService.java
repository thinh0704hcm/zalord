package zalord.message_service.service;

import zalord.message_service.dto.request.SendMessageRequest;
import zalord.message_service.dto.response.MessageReaderResponse;
import zalord.message_service.dto.response.MessageResponse;
import zalord.message_service.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

public interface IMessageService {

    MessageResponse send(UUID callerUserId, SendMessageRequest request);

    PageResponse<MessageResponse> history(UUID callerUserId, UUID conversationId, int page, int size);

    List<MessageReaderResponse> lastMessageReaders(UUID callerUserId, UUID conversationId);
    /**
     * Recall ("thu hồi cho tất cả"): mark the message as retracted. Only the
     * sender can recall their own message. Publishes message.recalled so live
     * clients update + the inbox preview is recomputed.
     */
    void recall(UUID callerUserId, UUID messageId);
}

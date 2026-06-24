package zalord.message_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zalord.message_service.config.RabbitMQConfig;
import zalord.message_service.dto.event.MessageReadEvent;
import zalord.message_service.dto.response.InboxItemResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.exception.InvalidRequestException;
import zalord.message_service.model.ConversationView;
import zalord.message_service.model.Message;
import zalord.message_service.model.OutboxEvent;
import zalord.message_service.repository.ConversationViewRepository;
import zalord.message_service.repository.MessageRepository;
import zalord.message_service.repository.OutboxEventRepository;
import zalord.message_service.service.IInboxService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class InboxServiceImpl implements IInboxService {

    private final ConversationViewRepository viewRepo;
    private final MessageRepository messageRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public InboxServiceImpl(ConversationViewRepository viewRepo,
                            MessageRepository messageRepo,
                            OutboxEventRepository outboxRepo,
                            ObjectMapper objectMapper) {
        this.viewRepo = viewRepo;
        this.messageRepo = messageRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InboxItemResponse> listInbox(UUID userId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ConversationView> result = viewRepo.findByUserIdOrderByLastMessageAtDesc(userId, pageable);

        List<InboxItemResponse> items = result.getContent().stream()
                .map(v -> InboxItemResponse.builder()
                        .conversationId(v.getConversationId())
                        .otherUserId(v.getOtherUserId())
                        .lastMessagePreview(v.getLastMessagePreview())
                        .lastMessageAt(v.getLastMessageAt())
                        .lastSenderId(v.getLastSenderId())
                        .unreadCount(v.getUnreadCount() == null ? 0 : v.getUnreadCount())
                        .lastReadMessageId(v.getLastReadMessageId())
                        .lastReadAt(v.getLastReadAt())
                        .build())
                .toList();

        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID conversationId, UUID messageId) {
        // Default to the latest message in the conversation when the caller
        // didn't pin one — the common case where the UI just sends "I'm
        // looking at this conv now".
        UUID resolved = messageId;
        if (resolved == null) {
            resolved = messageRepo.findFirstByConversationIdOrderByCreatedAtDesc(conversationId)
                    .map(Message::getId)
                    .orElse(null);
            if (resolved == null) {
                // Empty conversation — nothing to mark read.
                return;
            }
        } else {
            Message msg = messageRepo.findById(resolved)
                    .orElseThrow(() -> new InvalidRequestException("Message not found: " + messageId));
            if (!msg.getConversationId().equals(conversationId)) {
                throw new InvalidRequestException("Message does not belong to this conversation");
            }
        }

        Instant readAt = Instant.now();
        int updated = viewRepo.markRead(userId, conversationId, resolved, readAt);
        if (updated == 0) {
            // No conversation_view yet → caller has nothing in their inbox for
            // this conv. Skip silently (matches the prior behaviour).
            return;
        }

        enqueueReadOutbox(new MessageReadEvent(conversationId, userId, resolved, readAt));
        log.info("Marked read conv={} user={} lastMsg={}", conversationId, userId, resolved);
    }

    private void enqueueReadOutbox(MessageReadEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize message.read payload", ex);
        }
        OutboxEvent o = new OutboxEvent();
        o.setTopicExchange(RabbitMQConfig.MESSAGE_EXCHANGE);
        o.setRoutingKey(RabbitMQConfig.MESSAGE_READ_ROUTING_KEY);
        o.setPayload(payload);
        outboxRepo.save(o);
    }
}

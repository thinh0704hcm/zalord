package zalord.message_service.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zalord.message_service.config.RabbitMQConfig;
import zalord.message_service.dto.event.MessageCreatedEvent;
import zalord.message_service.enums.ConversationType;
import zalord.message_service.model.Conversation;
import zalord.message_service.model.ConversationMember;
import zalord.message_service.repository.ConversationMemberRepository;
import zalord.message_service.repository.ConversationRepository;
import zalord.message_service.repository.ConversationViewRepository;

import java.util.List;
import java.util.UUID;

/**
 * CQRS projector: consumes the message.created event published by
 * MessageServiceImpl (via outbox → RabbitMQ → back here) and updates the
 * conversation_views read model.
 *
 * Eventual consistency: a few hundred ms after POST /messages, inbox reflects
 * the new last-message preview and unread count. This decoupling — write does
 * NOT directly touch the read model — is the heart of CQRS.
 */
@Component
@Slf4j
public class InboxProjector {

    private final ConversationViewRepository viewRepo;
    private final ConversationMemberRepository memberRepo;
    private final ConversationRepository convRepo;
    private final ObjectMapper objectMapper;

    public InboxProjector(ConversationViewRepository viewRepo,
                          ConversationMemberRepository memberRepo,
                          ConversationRepository convRepo,
                          ObjectMapper objectMapper) {
        this.viewRepo = viewRepo;
        this.memberRepo = memberRepo;
        this.convRepo = convRepo;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.INBOX_PROJECTOR_QUEUE)
    @Transactional
    public void onMessageCreated(Message message) {
        // Take the raw Message (avoids Spring's auto-conversion which would try
        // to map the JSON body to byte[] — type mismatch and fail). We deserialize
        // ourselves using the same ObjectMapper that has JavaTimeModule registered.
        MessageCreatedEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), MessageCreatedEvent.class);
        } catch (Exception ex) {
            log.warn("InboxProjector: failed to parse event, dropping: {}", ex.getMessage());
            return;
        }

        Conversation conv = convRepo.findById(event.conversationId()).orElse(null);
        if (conv == null) {
            log.warn("InboxProjector: conversation {} not found, skipping", event.conversationId());
            return;
        }
        List<ConversationMember> members = memberRepo.findAllByConversationId(event.conversationId());
        if (members.isEmpty()) {
            log.warn("InboxProjector: no members for conv {}", event.conversationId());
            return;
        }

        String preview = truncate(event.content(), 200);

        for (ConversationMember m : members) {
            UUID memberId = m.getUserId();
            UUID otherUserId = conv.getType() == ConversationType.DIRECT
                    ? pickOther(members, memberId)
                    : null;
            viewRepo.upsertOnNewMessage(
                    memberId,
                    event.conversationId(),
                    otherUserId,
                    preview,
                    event.createdAt(),
                    event.senderId());
        }

        log.debug("Inbox projected for conv={} message={} members={}",
                event.conversationId(), event.messageId(), members.size());
    }

    private UUID pickOther(List<ConversationMember> members, UUID self) {
        return members.stream()
                .map(ConversationMember::getUserId)
                .filter(id -> !id.equals(self))
                .findFirst()
                .orElse(null);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

package zalord.message_service.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zalord.message_service.dto.event.MessageCreatedEvent;
import zalord.message_service.enums.ConversationType;
import zalord.message_service.eventbus.EventConsumer;
import zalord.message_service.model.Conversation;
import zalord.message_service.model.ConversationMember;
import zalord.message_service.repository.ConversationMemberRepository;
import zalord.message_service.repository.ConversationRepository;
import zalord.message_service.repository.ConversationViewRepository;

import java.util.List;
import java.util.UUID;

/**
 * CQRS projector for "message.created" → conversation_views read model.
 * Subscribes through the EventConsumer interface so it works on whichever
 * broker (RabbitMQ or Kafka) is active at startup — the listener-container
 * is created dynamically at PostConstruct, no @RabbitListener / @KafkaListener
 * annotations to pre-bind it to one backend.
 */
@Component
@Slf4j
public class InboxProjector {

    private static final String EVENT_NAME = "message.created";
    private static final String CONSUMER_GROUP = "message-inbox-projector";

    private final ConversationViewRepository viewRepo;
    private final ConversationMemberRepository memberRepo;
    private final ConversationRepository convRepo;
    private final ObjectMapper objectMapper;
    private final EventConsumer eventConsumer;
    private final InboxProjector self;

    public InboxProjector(ConversationViewRepository viewRepo,
                          ConversationMemberRepository memberRepo,
                          ConversationRepository convRepo,
                          ObjectMapper objectMapper,
                          EventConsumer eventConsumer,
                          @Lazy @Autowired InboxProjector self) {
        this.viewRepo = viewRepo;
        this.memberRepo = memberRepo;
        this.convRepo = convRepo;
        this.objectMapper = objectMapper;
        this.eventConsumer = eventConsumer;
        this.self = self;
    }

    @PostConstruct
    void register() {
        eventConsumer.subscribe(EVENT_NAME, CONSUMER_GROUP, self::project);
    }

    @Transactional
    public void project(byte[] body) {
        MessageCreatedEvent event;
        try {
            event = objectMapper.readValue(body, MessageCreatedEvent.class);
        } catch (Exception ex) {
            // Permanent: swallow so the broker doesn't requeue forever.
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

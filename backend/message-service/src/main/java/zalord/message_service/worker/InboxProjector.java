package zalord.message_service.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zalord.message_service.cache.ConversationTypeCache;
import zalord.message_service.dto.event.MessageCreatedEvent;
import zalord.message_service.dto.event.MessageRecalledEvent;
import zalord.message_service.enums.ConversationType;
import zalord.message_service.eventbus.EventConsumer;
import zalord.message_service.model.ConversationMember;
import zalord.message_service.model.Message;
import zalord.message_service.repository.ConversationMemberRepository;
import zalord.message_service.repository.ConversationRepository;
import zalord.message_service.repository.ConversationViewRepository;
import zalord.message_service.repository.MessageRepository;
import zalord.message_service.service.impl.MessageServiceImpl;

import java.time.Instant;
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

    private static final String EVENT_CREATED  = "message.created";
    private static final String EVENT_RECALLED = "message.recalled";
    private static final String CONSUMER_GROUP          = "message-inbox-projector";
    private static final String CONSUMER_GROUP_RECALLED = "message-inbox-projector-recall";

    private final ConversationViewRepository viewRepo;
    private final ConversationMemberRepository memberRepo;
    private final ConversationRepository convRepo;
    private final MessageRepository messageRepo;
    private final ConversationTypeCache convTypeCache;
    private final ObjectMapper objectMapper;
    private final EventConsumer eventConsumer;
    private final InboxProjector self;

    public InboxProjector(ConversationViewRepository viewRepo,
                          ConversationMemberRepository memberRepo,
                          ConversationRepository convRepo,
                          MessageRepository messageRepo,
                          ConversationTypeCache convTypeCache,
                          ObjectMapper objectMapper,
                          EventConsumer eventConsumer,
                          @Lazy @Autowired InboxProjector self) {
        this.viewRepo = viewRepo;
        this.memberRepo = memberRepo;
        this.convRepo = convRepo;
        this.messageRepo = messageRepo;
        this.convTypeCache = convTypeCache;
        this.objectMapper = objectMapper;
        this.eventConsumer = eventConsumer;
        this.self = self;
    }

    @PostConstruct
    void register() {
        eventConsumer.subscribe(EVENT_CREATED,  CONSUMER_GROUP,          self::project);
        eventConsumer.subscribe(EVENT_RECALLED, CONSUMER_GROUP_RECALLED, self::projectRecall);
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

        // Cached: conv type is immutable, so this avoids hitting Postgres for
        // every single message.created event after the first one per conv.
        ConversationType convType = convTypeCache.getOrLoad(event.conversationId(),
                () -> convRepo.findById(event.conversationId()).map(c -> c.getType()).orElse(null));
        if (convType == null) {
            log.warn("InboxProjector: conversation {} not found, skipping", event.conversationId());
            return;
        }
        List<ConversationMember> members = memberRepo.findAllByConversationId(event.conversationId());
        if (members.isEmpty()) {
            log.warn("InboxProjector: no members for conv {}", event.conversationId());
            return;
        }

        String preview = previewOf(event);

        for (ConversationMember m : members) {
            if (m.getLeftAt() != null) continue;
            
            UUID memberId = m.getUserId();
            UUID otherUserId = convType == ConversationType.DIRECT
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

    /**
     * Recall projection. Rewrites the inbox preview ONLY for views whose
     * currently-shown last message IS the recalled one — older or newer
     * messages aren't affected. Recall doesn't change unread counts (people
     * already saw the message before it was retracted).
     */
    @Transactional
    public void projectRecall(byte[] body) {
        MessageRecalledEvent event;
        try {
            event = objectMapper.readValue(body, MessageRecalledEvent.class);
        } catch (Exception ex) {
            log.warn("InboxProjector: bad recall payload, dropping: {}", ex.getMessage());
            return;
        }

        // Need the recalled msg's created_at to scope the WHERE clause —
        // the event only carries recalled_at (when it was retracted), not
        // when it was originally sent. One indexed lookup.
        Message recalled = messageRepo.findById(event.messageId()).orElse(null);
        if (recalled == null) {
            log.warn("InboxProjector: recalled msg {} not found", event.messageId());
            return;
        }

        // Pick the new preview source: the most recent NON-recalled message
        // in the conv. May be null (this was the only message ever sent).
        Message nextSource = messageRepo
                .findFirstByConversationIdAndRecalledAtIsNullOrderByCreatedAtDesc(event.conversationId())
                .orElse(null);

        String newPreview;
        Instant newAt;
        UUID newSender;
        if (nextSource != null) {
            newPreview = MessageServiceImpl.snapshotPreview(nextSource);
            newAt      = nextSource.getCreatedAt();
            newSender  = nextSource.getSenderId();
        } else {
            newPreview = "Tin nhắn đã được thu hồi";
            newAt      = event.recalledAt();
            newSender  = event.senderId();
        }

        int touched = viewRepo.rewritePreviewForRecalled(
                event.conversationId(),
                recalled.getCreatedAt(),
                event.senderId(),
                newPreview,
                newAt,
                newSender);
        log.debug("Inbox recall-projected conv={} msg={} viewsTouched={}",
                event.conversationId(), event.messageId(), touched);
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

    private String previewOf(MessageCreatedEvent event) {
        String content = event.content();
        int n = event.attachmentIds() == null ? 0 : event.attachmentIds().size();
        if (content != null && !content.isBlank()) {
            return truncate(content, 200);
        }
        if (n > 0) return "📎 " + n + (n == 1 ? " tệp đính kèm" : " tệp đính kèm");
        return null;
    }
}

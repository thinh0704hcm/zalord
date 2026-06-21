package zalord.message_service.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zalord.message_service.config.RabbitMQConfig;
import zalord.message_service.dto.event.GroupCreatedEvent;
import zalord.message_service.dto.event.GroupMemberAddedEvent;
import zalord.message_service.dto.event.GroupMemberRemovedEvent;
import zalord.message_service.enums.ConversationType;
import zalord.message_service.model.Conversation;
import zalord.message_service.model.ConversationMember;
import zalord.message_service.cache.ConversationMembersCache;
import zalord.message_service.repository.ConversationMemberRepository;
import zalord.message_service.repository.ConversationRepository;
import zalord.message_service.repository.ConversationViewRepository;

import java.util.List;
import java.util.UUID;

/**
 * Projects group-service events into message-service's local data so the
 * existing message-send / inbox flow works transparently for GROUP convs:
 *   group.created          → Conversation(id=groupId, type=GROUP) + members + views
 *   group.member.added     → ConversationMember + ConversationView for the new user
 *   group.member.removed   → delete ConversationMember + ConversationView for that user
 *
 * Critical convention: group.id == conversation.id (no mapping table).
 * The Conversation entity uses @GeneratedValue(UUID); Hibernate only auto-
 * generates when id is null, so setting id explicitly before save() is honored.
 */
@Component
@Slf4j
public class GroupEventConsumer {

    private final ObjectMapper objectMapper;
    private final ConversationRepository convRepo;
    private final ConversationMemberRepository memberRepo;
    private final ConversationViewRepository viewRepo;
    private final ConversationMembersCache membersCache;

    public GroupEventConsumer(ObjectMapper objectMapper,
                              ConversationRepository convRepo,
                              ConversationMemberRepository memberRepo,
                              ConversationViewRepository viewRepo,
                              ConversationMembersCache membersCache) {
        this.objectMapper = objectMapper;
        this.convRepo = convRepo;
        this.memberRepo = memberRepo;
        this.viewRepo = viewRepo;
        this.membersCache = membersCache;
    }

    @RabbitListener(queues = RabbitMQConfig.GROUP_EVENTS_QUEUE)
    @Transactional
    public void onGroupEvent(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        byte[] body = message.getBody();
        try {
            switch (routingKey) {
                case "group.created"        -> handleCreated(body);
                case "group.member.added"   -> handleMemberAdded(body);
                case "group.member.removed" -> handleMemberRemoved(body);
                case "group.updated"        -> { /* no-op for now; name not cached locally */ }
                default -> log.debug("Group event unhandled: {}", routingKey);
            }
        } catch (Exception ex) {
            // Permanent (parse / data) — log and drop. Don't requeue.
            log.warn("Group event handler failed key={}: {}", routingKey, ex.getMessage());
        }
    }

    private void handleCreated(byte[] body) throws Exception {
        GroupCreatedEvent e = objectMapper.readValue(body, GroupCreatedEvent.class);

        if (convRepo.existsById(e.groupId())) {
            log.debug("Group conv already projected: {}", e.groupId());
            return;
        }

        // Native INSERT with explicit id — bypasses Hibernate's @GeneratedValue
        // detach-detection, idempotent on re-delivery via ON CONFLICT.
        convRepo.insertWithExplicitId(e.groupId(), ConversationType.GROUP.name());

        List<UUID> members = e.memberIds();
        for (UUID userId : members) {
            ConversationMember m = new ConversationMember();
            m.setConversationId(e.groupId());
            m.setUserId(userId);
            memberRepo.save(m);
            // CQRS view: otherUserId is null for groups (no single "other party").
            viewRepo.initView(userId, e.groupId(), null);
        }
        // Bulk SADD to Redis for media-service authz cache.
        membersCache.addMembers(e.groupId(), members);
        log.info("Projected group {} ({}) with {} members",
                e.groupId(), e.name(), members.size());
    }

    private void handleMemberAdded(byte[] body) throws Exception {
        GroupMemberAddedEvent e = objectMapper.readValue(body, GroupMemberAddedEvent.class);

        if (memberRepo.existsByConversationIdAndUserId(e.groupId(), e.userId())) {
            return; // idempotent — already projected
        }
        ConversationMember m = new ConversationMember();
        m.setConversationId(e.groupId());
        m.setUserId(e.userId());
        memberRepo.save(m);
        viewRepo.initView(e.userId(), e.groupId(), null);
        membersCache.addMember(e.groupId(), e.userId());
        log.info("Projected member-added group={} user={}", e.groupId(), e.userId());
    }

    private void handleMemberRemoved(byte[] body) throws Exception {
        GroupMemberRemovedEvent e = objectMapper.readValue(body, GroupMemberRemovedEvent.class);

        // Use native deletes — JpaRepository deleteBy methods exist but it's
        // cleaner to issue a single DELETE for each.
        memberRepo.findAllByConversationId(e.groupId()).stream()
                .filter(m -> m.getUserId().equals(e.userId()))
                .findFirst()
                .ifPresent(memberRepo::delete);

        // Drop the user's view of this group from their inbox.
        viewRepo.deleteUserConversationView(e.userId(), e.groupId());

        // Evict from Redis cache — they can no longer download attachments here.
        membersCache.removeMember(e.groupId(), e.userId());

        log.info("Projected member-removed group={} user={}", e.groupId(), e.userId());
    }
}

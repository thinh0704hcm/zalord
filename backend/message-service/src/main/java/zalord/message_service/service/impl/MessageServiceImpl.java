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
import zalord.message_service.dto.event.MessageCreatedEvent;
import zalord.message_service.dto.request.SendMessageRequest;
import zalord.message_service.dto.response.MessageResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.exception.NotMemberException;
import zalord.message_service.model.ConversationMember;
import zalord.message_service.model.Message;
import zalord.message_service.model.OutboxEvent;
import zalord.message_service.repository.ConversationMemberRepository;
import zalord.message_service.repository.MessageRepository;
import zalord.message_service.repository.OutboxEventRepository;
import zalord.message_service.service.IMessageService;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class MessageServiceImpl implements IMessageService {

    private final MessageRepository messageRepo;
    private final ConversationMemberRepository memberRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public MessageServiceImpl(MessageRepository messageRepo,
                              ConversationMemberRepository memberRepo,
                              OutboxEventRepository outboxRepo,
                              ObjectMapper objectMapper) {
        this.messageRepo = messageRepo;
        this.memberRepo = memberRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public MessageResponse send(UUID caller, SendMessageRequest req) {
        List<ConversationMember> members = memberRepo.findAllByConversationId(req.conversationId());
        if (members.isEmpty()) {
            throw new NotMemberException("Conversation not found or you are not a member");
        }
        List<UUID> memberIds = members.stream().map(ConversationMember::getUserId).toList();
        if (!memberIds.contains(caller)) {
            throw new NotMemberException("You are not a member of this conversation");
        }

        Message msg = new Message();
        msg.setConversationId(req.conversationId());
        msg.setSenderId(caller);
        msg.setContent(req.content());
        msg = messageRepo.save(msg);

        // Same Postgres tx → either both committed or both rolled back.
        // OutboxScheduler picks up unpublished rows and ships them to RabbitMQ.
        List<UUID> recipients = memberIds.stream().filter(id -> !id.equals(caller)).toList();
        MessageCreatedEvent event = new MessageCreatedEvent(
                msg.getId(), msg.getConversationId(), msg.getSenderId(),
                recipients, msg.getContent(), msg.getCreatedAt());
        enqueueOutbox(event);

        log.info("Message sent id={} conv={} sender={} recipients={}",
                msg.getId(), msg.getConversationId(), caller, recipients.size());

        return MessageResponse.builder()
                .id(msg.getId())
                .conversationId(msg.getConversationId())
                .senderId(msg.getSenderId())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> history(UUID caller, UUID conversationId, int page, int size) {
        boolean isMember = memberRepo.existsByConversationIdAndUserId(conversationId, caller);
        if (!isMember) {
            throw new NotMemberException("You are not a member of this conversation");
        }
        if (page < 1) page = 1;
        if (size < 1) size = 50;
        if (size > 200) size = 200;

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Message> result = messageRepo.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        List<MessageResponse> items = result.getContent().stream()
                .map(m -> MessageResponse.builder()
                        .id(m.getId())
                        .conversationId(m.getConversationId())
                        .senderId(m.getSenderId())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();

        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    private void enqueueOutbox(MessageCreatedEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
        OutboxEvent o = new OutboxEvent();
        o.setTopicExchange(RabbitMQConfig.MESSAGE_EXCHANGE);
        o.setRoutingKey(RabbitMQConfig.MESSAGE_CREATED_ROUTING_KEY);
        o.setPayload(payload);
        outboxRepo.save(o);
    }
}

package zalord.message_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zalord.media.v1.InvalidAttachment;
import zalord.message_service.config.RabbitMQConfig;
import zalord.message_service.dto.event.MessageCreatedEvent;
import zalord.message_service.dto.request.SendMessageRequest;
import zalord.message_service.dto.response.MessageReaderResponse;
import zalord.message_service.dto.response.MessageResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.exception.InvalidRequestException;
import zalord.message_service.exception.NotMemberException;
import zalord.message_service.grpc.MediaGrpcClient;
import zalord.message_service.model.ConversationMember;
import zalord.message_service.model.Message;
import zalord.message_service.model.MessageAttachment;
import zalord.message_service.model.OutboxEvent;
import zalord.message_service.repository.ConversationMemberRepository;
import zalord.message_service.repository.MessageAttachmentRepository;
import zalord.message_service.repository.MessageReadReceiptRepository;
import zalord.message_service.repository.MessageRepository;
import zalord.message_service.repository.OutboxEventRepository;
import zalord.message_service.service.IMessageService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageServiceImpl implements IMessageService {

    private final MessageRepository messageRepo;
    private final ConversationMemberRepository memberRepo;
    private final MessageReadReceiptRepository readReceiptRepo;
    private final MessageAttachmentRepository attachmentRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final MediaGrpcClient mediaGrpc;

    public MessageServiceImpl(MessageRepository messageRepo,
                              ConversationMemberRepository memberRepo,
                              MessageReadReceiptRepository readReceiptRepo,
                              MessageAttachmentRepository attachmentRepo,
                              OutboxEventRepository outboxRepo,
                              ObjectMapper objectMapper,
                              MediaGrpcClient mediaGrpc) {
        this.messageRepo = messageRepo;
        this.memberRepo = memberRepo;
        this.readReceiptRepo = readReceiptRepo;
        this.attachmentRepo = attachmentRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.mediaGrpc = mediaGrpc;
    }

    @Override
    @Transactional
    public MessageResponse send(UUID caller, SendMessageRequest req) {
        // De-dup ids while preserving caller order (used for `position`).
        List<UUID> attachmentIds = req.attachmentIds() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(req.attachmentIds()));

        String content = req.content() == null ? "" : req.content();
        if (content.isBlank() && attachmentIds.isEmpty()) {
            throw new InvalidRequestException("Message must have content or at least one attachment");
        }

        List<ConversationMember> members = memberRepo.findAllByConversationId(req.conversationId());
        if (members.isEmpty()) {
            throw new NotMemberException("Conversation not found or you are not a member");
        }
        List<UUID> memberIds = members.stream().map(ConversationMember::getUserId).toList();
        if (!memberIds.contains(caller)) {
            throw new NotMemberException("You are not a member of this conversation");
        }

        // Sync gRPC into media-service — reject bogus attachments BEFORE we
        // persist anything or fan out an event. Short deadline (3s) since the
        // POST is user-facing.
        if (!attachmentIds.isEmpty()) {
            MediaGrpcClient.Result vr = mediaGrpc.validate(caller, req.conversationId(), attachmentIds);
            if (!vr.valid()) {
                String detail = vr.invalid().stream()
                        .map(i -> i.getMediaId() + " (" + i.getReason() + ")")
                        .collect(Collectors.joining(", "));
                throw new InvalidRequestException("Invalid attachments: " + detail);
            }
        }

        Message msg = new Message();
        msg.setConversationId(req.conversationId());
        msg.setSenderId(caller);
        msg.setContent(content);
        msg = messageRepo.save(msg);

        for (int i = 0; i < attachmentIds.size(); i++) {
            attachmentRepo.save(new MessageAttachment(msg.getId(), attachmentIds.get(i), (short) i));
        }

        // Same Postgres tx → either both committed or both rolled back.
        // OutboxScheduler picks up unpublished rows and ships them to RabbitMQ.
        List<UUID> recipients = memberIds.stream().filter(id -> !id.equals(caller)).toList();
        MessageCreatedEvent event = new MessageCreatedEvent(
                msg.getId(), msg.getConversationId(), msg.getSenderId(),
                recipients, msg.getContent(), attachmentIds, msg.getCreatedAt());
        enqueueOutbox(event);

        log.info("Message sent id={} conv={} sender={} recipients={} attachments={}",
                msg.getId(), msg.getConversationId(), caller, recipients.size(), attachmentIds.size());

        return MessageResponse.builder()
                .id(msg.getId())
                .conversationId(msg.getConversationId())
                .senderId(msg.getSenderId())
                .content(msg.getContent())
                .attachmentIds(attachmentIds)
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

        List<UUID> ids = result.getContent().stream().map(Message::getId).toList();
        Map<UUID, List<UUID>> attachmentsByMsg = ids.isEmpty() ? Map.of()
                : attachmentRepo.findByMessageIdIn(ids).stream()
                .sorted(Comparator.comparingInt(a -> a.getPosition()))
                .collect(Collectors.groupingBy(
                        a -> a.getId().getMessageId(),
                        Collectors.mapping(a -> a.getId().getMediaId(), Collectors.toList())));

        List<MessageResponse> items = result.getContent().stream()
                .map(m -> MessageResponse.builder()
                        .id(m.getId())
                        .conversationId(m.getConversationId())
                        .senderId(m.getSenderId())
                        .content(m.getContent())
                        .attachmentIds(attachmentsByMsg.getOrDefault(m.getId(), List.of()))
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();

        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageReaderResponse> lastMessageReaders(UUID caller, UUID conversationId) {
        boolean isMember = memberRepo.existsByConversationIdAndUserId(conversationId, caller);
        if (!isMember) {
            throw new NotMemberException("You are not a member of this conversation");
        }

        Optional<Message> latest = messageRepo.findFirstByConversationIdOrderByCreatedAtDesc(conversationId);
        if (latest.isEmpty()) {
            return List.of();
        }

        Message latestMessage = latest.get();
        return readReceiptRepo.findReadersOfMessage(latestMessage.getId(), caller, latestMessage.getSenderId()).stream()
                .map(receipt -> new MessageReaderResponse(receipt.getUserId(), receipt.getReadAt()))
                .toList();
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

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
import zalord.message_service.dto.event.MessageRecalledEvent;
import zalord.message_service.dto.request.SendMessageRequest;
import zalord.message_service.dto.response.MessageReaderResponse;
import zalord.message_service.dto.response.MessageResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.dto.response.ReplyToSnippet;
import zalord.message_service.exception.ForbiddenException;
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

import java.time.Instant;
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

    private static final int PREVIEW_MAX = 200;

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

        ConversationMember callerMember = members.stream()
                .filter(m -> m.getUserId().equals(caller))
                .findFirst()
                .orElseThrow(() -> new NotMemberException("You are not a member of this conversation"));
        
        if (callerMember.getLeftAt() != null) {
            throw new NotMemberException("You are no longer a member of this conversation");
        }

        List<UUID> memberIds = members.stream()
                .filter(m -> m.getLeftAt() == null)
                .map(ConversationMember::getUserId)
                .toList();

        // Validate the reply target (if any) BEFORE we touch media — cheaper
        // failure first. Allowed to reply to a recalled message; the UI can
        // render the snapshot grey.
        Message replyTarget = null;
        if (req.replyToMessageId() != null) {
            replyTarget = messageRepo.findById(req.replyToMessageId())
                    .orElseThrow(() -> new InvalidRequestException(
                            "replyToMessageId not found: " + req.replyToMessageId()));
            if (!replyTarget.getConversationId().equals(req.conversationId())) {
                throw new InvalidRequestException("Cannot reply across conversations");
            }
        }

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
        if (replyTarget != null) {
            msg.setReplyToMessageId(replyTarget.getId());
            msg.setReplyToSenderId(replyTarget.getSenderId());
            // Snapshot uses CURRENT content of the quoted message — if the
            // original is later recalled, the snapshot still shows what was
            // visible at quote-time. For attachment-only quoted msgs, fall
            // back to a count badge so the snippet is never empty.
            msg.setReplyToPreview(snapshotPreview(replyTarget));
        }
        msg = messageRepo.save(msg);

        for (int i = 0; i < attachmentIds.size(); i++) {
            attachmentRepo.save(new MessageAttachment(msg.getId(), attachmentIds.get(i), (short) i));
        }

        List<UUID> recipients = memberIds.stream().filter(id -> !id.equals(caller)).toList();
        MessageCreatedEvent.ReplyToSnippet eventReply = replyTarget == null ? null
                : new MessageCreatedEvent.ReplyToSnippet(
                        replyTarget.getId(),
                        replyTarget.getSenderId(),
                        msg.getReplyToPreview());
        MessageCreatedEvent event = new MessageCreatedEvent(
                msg.getId(), msg.getConversationId(), msg.getSenderId(),
                recipients, msg.getContent(), attachmentIds, msg.getCreatedAt(),
                eventReply);
        enqueueOutbox(RabbitMQConfig.MESSAGE_CREATED_ROUTING_KEY, event);

        log.info("Message sent id={} conv={} sender={} recipients={} attachments={} replyTo={}",
                msg.getId(), msg.getConversationId(), caller, recipients.size(),
                attachmentIds.size(), msg.getReplyToMessageId());

        return toResponse(msg, attachmentIds);
    }

    @Override
    @Transactional
    public void recall(UUID caller, UUID messageId) {
        Message msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new InvalidRequestException("Message not found: " + messageId));
        if (!msg.getSenderId().equals(caller)) {
            throw new ForbiddenException("Only the sender can recall this message");
        }
        if (msg.getRecalledAt() != null) {
            // Idempotent: no event re-published, no error.
            return;
        }

        Instant recalledAt = Instant.now();
        msg.setRecalledAt(recalledAt);
        // Don't blank content/attachments in storage — the columns may be
        // referenced by previously sent reply snapshots, and history needs
        // ordering by created_at. The response layer hides the body.

        MessageRecalledEvent event = new MessageRecalledEvent(
                msg.getId(), msg.getConversationId(), msg.getSenderId(), recalledAt);
        enqueueOutbox(RabbitMQConfig.MESSAGE_RECALLED_ROUTING_KEY, event);

        log.info("Message recalled id={} conv={} sender={}",
                msg.getId(), msg.getConversationId(), caller);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> history(UUID caller, UUID conversationId, int page, int size) {
        ConversationMember member = memberRepo.findByConversationIdAndUserId(conversationId, caller)
                .orElseThrow(() -> new NotMemberException("You are not a member of this conversation"));

        if (page < 1) page = 1;
        if (size < 1) size = 50;
        if (size > 200) size = 200;

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Message> result;
        if (member.getLeftAt() != null) {
            result = messageRepo.findByConversationIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(conversationId, member.getLeftAt(), pageable);
        } else {
            result = messageRepo.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);
        }

        List<UUID> ids = result.getContent().stream().map(Message::getId).toList();
        Map<UUID, List<UUID>> attachmentsByMsg = ids.isEmpty() ? Map.of()
                : attachmentRepo.findByMessageIdIn(ids).stream()
                .sorted(Comparator.comparingInt(MessageAttachment::getPosition))
                .collect(Collectors.groupingBy(
                        a -> a.getId().getMessageId(),
                        Collectors.mapping(a -> a.getId().getMediaId(), Collectors.toList())));

        List<MessageResponse> items = result.getContent().stream()
                .map(m -> toResponse(m, attachmentsByMsg.getOrDefault(m.getId(), List.of())))
                .toList();

        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageReaderResponse> lastMessageReaders(UUID caller, UUID conversationId) {
        ConversationMember member = memberRepo.findByConversationIdAndUserId(conversationId, caller)
                .orElseThrow(() -> new NotMemberException("You are not a member of this conversation"));

        Optional<Message> latest = messageRepo.findFirstByConversationIdOrderByCreatedAtDesc(conversationId);
        if (latest.isEmpty()) {
            return List.of();
        }

        Message latestMessage = latest.get();
        return readReceiptRepo.findReadersOfMessage(latestMessage.getId(), caller, latestMessage.getSenderId()).stream()
                .map(receipt -> new MessageReaderResponse(receipt.getUserId(), receipt.getReadAt()))
                .toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private MessageResponse toResponse(Message m, List<UUID> attachmentIds) {
        boolean recalled = m.getRecalledAt() != null;
        ReplyToSnippet reply = m.getReplyToMessageId() == null ? null
                : ReplyToSnippet.builder()
                        .messageId(m.getReplyToMessageId())
                        .senderId(m.getReplyToSenderId())
                        .preview(m.getReplyToPreview())
                        .build();
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderId(m.getSenderId())
                // Body is blanked client-side via the recalledAt marker rather
                // than being null-ed here — keeps the response shape stable.
                .content(recalled ? "" : m.getContent())
                .attachmentIds(recalled ? List.of() : attachmentIds)
                .createdAt(m.getCreatedAt())
                .recalledAt(m.getRecalledAt())
                .replyTo(reply)
                .build();
    }

    /** Used both for reply snapshots and (in InboxProjector) preview recompute. */
    public static String snapshotPreview(Message m) {
        if (m.getRecalledAt() != null) {
            return "Tin nhắn đã được thu hồi";
        }
        if (m.getContent() != null && !m.getContent().isBlank()) {
            String c = m.getContent();
            return c.length() <= PREVIEW_MAX ? c : c.substring(0, PREVIEW_MAX);
        }
        // Content blank → must be attachment-only. We don't have the attachment
        // count here cheaply; use a generic glyph so the snippet is non-empty.
        return "📎 Tệp đính kèm";
    }

    private void enqueueOutbox(String routingKey, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
        OutboxEvent o = new OutboxEvent();
        o.setTopicExchange(RabbitMQConfig.MESSAGE_EXCHANGE);
        o.setRoutingKey(routingKey);
        o.setPayload(payload);
        outboxRepo.save(o);
    }
}

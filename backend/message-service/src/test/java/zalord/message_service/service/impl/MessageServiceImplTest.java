package zalord.message_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import zalord.message_service.dto.request.SendMessageRequest;
import zalord.message_service.dto.response.MessageResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.exception.ForbiddenException;
import zalord.message_service.exception.InvalidRequestException;
import zalord.message_service.exception.NotMemberException;
import zalord.message_service.grpc.MediaGrpcClient;
import zalord.message_service.model.ConversationMember;
import zalord.message_service.model.Message;
import zalord.message_service.repository.ConversationMemberRepository;
import zalord.message_service.repository.MessageAttachmentRepository;
import zalord.message_service.repository.MessageReadReceiptRepository;
import zalord.message_service.repository.MessageRepository;
import zalord.message_service.repository.OutboxEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageRepository messageRepo;
    @Mock
    private ConversationMemberRepository memberRepo;
    @Mock
    private MessageReadReceiptRepository readReceiptRepo;
    @Mock
    private MessageAttachmentRepository attachmentRepo;
    @Mock
    private OutboxEventRepository outboxRepo;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private MediaGrpcClient mediaGrpc;

    @InjectMocks
    private MessageServiceImpl messageService;

    private UUID callerId;
    private UUID conversationId;
    private UUID messageId;
    private Message testMessage;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        callerId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        messageId = UUID.randomUUID();

        testMessage = new Message();
        testMessage.setId(messageId);
        testMessage.setConversationId(conversationId);
        testMessage.setSenderId(callerId);
        testMessage.setContent("Hello World");
        testMessage.setCreatedAt(Instant.now());

        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void send_Success() {
        SendMessageRequest req = new SendMessageRequest(conversationId, "Hello World", null, null);
        
        ConversationMember callerMember = new ConversationMember();
        callerMember.setUserId(callerId);
        
        ConversationMember otherMember = new ConversationMember();
        otherMember.setUserId(UUID.randomUUID());
        
        when(memberRepo.findAllByConversationId(conversationId)).thenReturn(List.of(callerMember, otherMember));
        when(messageRepo.save(any(Message.class))).thenReturn(testMessage);

        MessageResponse response = messageService.send(callerId, req);

        assertNotNull(response);
        assertEquals("Hello World", response.getContent());
        assertEquals(callerId, response.getSenderId());
        
        verify(messageRepo).save(any(Message.class));
        verify(outboxRepo).save(any());
    }

    @Test
    void send_Failure_NotMember() {
        SendMessageRequest req = new SendMessageRequest(conversationId, "Hello World", null, null);
        
        when(memberRepo.findAllByConversationId(conversationId)).thenReturn(List.of());

        assertThrows(NotMemberException.class, () -> {
            messageService.send(callerId, req);
        });

        verify(messageRepo, never()).save(any(Message.class));
    }

    @Test
    void recall_Success() {
        when(messageRepo.findById(messageId)).thenReturn(Optional.of(testMessage));

        messageService.recall(callerId, messageId);

        assertNotNull(testMessage.getRecalledAt());
        verify(outboxRepo).save(any());
    }

    @Test
    void recall_Failure_NotSender() {
        UUID otherUserId = UUID.randomUUID();
        when(messageRepo.findById(messageId)).thenReturn(Optional.of(testMessage));

        assertThrows(ForbiddenException.class, () -> {
            messageService.recall(otherUserId, messageId);
        });

        assertNull(testMessage.getRecalledAt());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void history_Success() {
        ConversationMember callerMember = new ConversationMember();
        callerMember.setUserId(callerId);
        when(memberRepo.findByConversationIdAndUserId(conversationId, callerId))
                .thenReturn(Optional.of(callerMember));

        Page<Message> messagePage = new PageImpl<>(List.of(testMessage));
        when(messageRepo.findByConversationIdOrderByCreatedAtDesc(eq(conversationId), any(Pageable.class)))
                .thenReturn(messagePage);

        PageResponse<MessageResponse> response = messageService.history(callerId, conversationId, null, 50);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Hello World", response.getContent().get(0).getContent());
    }
}

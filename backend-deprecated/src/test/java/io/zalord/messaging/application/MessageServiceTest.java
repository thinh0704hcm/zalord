package io.zalord.messaging.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.zalord.common.exception.MessageNotFoundException;
import io.zalord.common.exception.UnauthorizedException;
import io.zalord.messaging.application.commands.DeleteMessageCommand;
import io.zalord.messaging.application.commands.SendMessageCommand;
import io.zalord.messaging.domain.entities.Message;
import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.dto.response.MessageResponse;
import io.zalord.messaging.port.ChatAccessPort;
import io.zalord.messaging.repository.MessageRepository;

import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class MessageServiceTest {

    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHAT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MSG_ID    = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Mock private MessageRepository messageRepository;
    @Mock private ChatAccessPort chatAccessPort;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, chatAccessPort);
    }

    @Nested
    class SendMessageTests {

        private SendMessageCommand validCmd;

        @BeforeEach
        void setUp() {
            validCmd = new SendMessageCommand(ACTOR_ID, CHAT_ID, ContentType.TEXT, null);
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("MSG-SEND-01: Should throw when actor cannot send in chat")
        void sendMessage_shouldThrow_whenCannotSend() {
            when(chatAccessPort.canSendMessage(CHAT_ID, ACTOR_ID)).thenReturn(false);

            assertThrows(UnauthorizedException.class, () -> messageService.sendMessage(validCmd));

            verify(messageRepository, never()).save(any());
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("MSG-SEND-02: Should save message and return response on success")
        void sendMessage_shouldSaveAndReturn_whenAuthorized() {
            Message saved = Message.builder()
                    .id(MSG_ID)
                    .chatId(CHAT_ID)
                    .senderId(ACTOR_ID)
                    .contentType(ContentType.TEXT)
                    .payload(null)
                    .build();

            when(chatAccessPort.canSendMessage(CHAT_ID, ACTOR_ID)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenReturn(saved);

            MessageResponse response = messageService.sendMessage(validCmd);

            assertEquals(CHAT_ID, response.chatId());
            assertEquals(ACTOR_ID, response.senderId());
            assertEquals(ContentType.TEXT, response.contentType());
            verify(messageRepository).save(any(Message.class));
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("MSG-SEND-03: Should call updateLastActivity with correct chatId on success")
        void sendMessage_shouldUpdateLastActivity_whenAuthorized() {
            Message saved = Message.builder()
                    .id(MSG_ID)
                    .chatId(CHAT_ID)
                    .senderId(ACTOR_ID)
                    .contentType(ContentType.TEXT)
                    .build();

            when(chatAccessPort.canSendMessage(CHAT_ID, ACTOR_ID)).thenReturn(true);
            when(messageRepository.save(any(Message.class))).thenReturn(saved);

            messageService.sendMessage(validCmd);

            verify(chatAccessPort).updateLastActivity(eq(CHAT_ID), any());
        }

    }

    @Nested
    class DeleteMessageTests {

        @Test
        @Tag("unit-messaging")
        @DisplayName("MSG-DEL-01: Should throw when message is not found")
        void deleteMessage_shouldThrow_whenMessageNotFound() {
            when(messageRepository.findById(MSG_ID)).thenReturn(Optional.empty());

            assertThrows(MessageNotFoundException.class,
                    () -> messageService.deleteMessage(new DeleteMessageCommand(ACTOR_ID, MSG_ID)));
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("MSG-DEL-02: Should throw when actor is not the sender")
        void deleteMessage_shouldThrow_whenActorIsNotSender() {
            Message message = Message.builder()
                    .id(MSG_ID)
                    .chatId(CHAT_ID)
                    .senderId(OTHER_ID)
                    .contentType(ContentType.TEXT)
                    .build();

            when(messageRepository.findById(MSG_ID)).thenReturn(Optional.of(message));

            assertThrows(UnauthorizedException.class,
                    () -> messageService.deleteMessage(new DeleteMessageCommand(ACTOR_ID, MSG_ID)));
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("MSG-DEL-03: Should mark message as DELETED and clear payload when actor is sender")
        void deleteMessage_shouldMarkDeleted_whenActorIsSender() {
            Message message = Message.builder()
                    .id(MSG_ID)
                    .chatId(CHAT_ID)
                    .senderId(ACTOR_ID)
                    .contentType(ContentType.TEXT)
                    .payload(null)
                    .build();

            when(messageRepository.findById(MSG_ID)).thenReturn(Optional.of(message));

            MessageResponse response = messageService.deleteMessage(new DeleteMessageCommand(ACTOR_ID, MSG_ID));

            assertEquals(ContentType.DELETED, response.contentType());
            assertNull(response.payload());
        }
    }
}

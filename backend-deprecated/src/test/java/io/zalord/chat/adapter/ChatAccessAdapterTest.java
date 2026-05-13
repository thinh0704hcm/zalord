package io.zalord.chat.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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

import io.zalord.chat.domain.entities.Chat;
import io.zalord.chat.domain.entities.ChatMember;
import io.zalord.chat.domain.entities.ChatMemberId;
import io.zalord.chat.domain.enums.ChatMemberRole;
import io.zalord.chat.domain.enums.ChatType;
import io.zalord.chat.repository.ChatMemberRepository;
import io.zalord.chat.repository.ChatRepository;

@ExtendWith(MockitoExtension.class)
public class ChatAccessAdapterTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHAT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock private ChatRepository chatRepository;
    @Mock private ChatMemberRepository chatMemberRepository;

    private ChatAccessAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ChatAccessAdapter(chatRepository, chatMemberRepository);
    }

    @Nested
    class CanSendMessageTests {

        @Test
        @Tag("unit-messaging")
        @DisplayName("ADAPTER-CAN-01: Should return false when chat does not exist")
        void canSendMessage_shouldReturnFalse_whenChatNotFound() {
            when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.empty());

            assertFalse(adapter.canSendMessage(CHAT_ID, ACTOR_ID));
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("ADAPTER-CAN-02: Should return false when actor is not a member")
        void canSendMessage_shouldReturnFalse_whenMemberNotFound() {
            Chat chat = Chat.builder().id(CHAT_ID).chatType(ChatType.GROUP).chatName("test").build();

            when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(chat));
            when(chatMemberRepository.findById(new ChatMemberId(CHAT_ID, ACTOR_ID)))
                    .thenReturn(Optional.empty());

            assertFalse(adapter.canSendMessage(CHAT_ID, ACTOR_ID));
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("ADAPTER-CAN-03: Should return false when chat is COMMUNITY and actor is MEMBER")
        void canSendMessage_shouldReturnFalse_whenCommunityMember() {
            Chat chat = Chat.builder().id(CHAT_ID).chatType(ChatType.COMMUNITY).chatName("test").build();
            ChatMember member = ChatMember.builder()
                    .chatId(CHAT_ID).memberId(ACTOR_ID).role(ChatMemberRole.MEMBER).build();

            when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(chat));
            when(chatMemberRepository.findById(new ChatMemberId(CHAT_ID, ACTOR_ID)))
                    .thenReturn(Optional.of(member));

            assertFalse(adapter.canSendMessage(CHAT_ID, ACTOR_ID));
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("ADAPTER-CAN-04: Should return true when chat is COMMUNITY and actor is ADMIN")
        void canSendMessage_shouldReturnTrue_whenCommunityAdmin() {
            Chat chat = Chat.builder().id(CHAT_ID).chatType(ChatType.COMMUNITY).chatName("test").build();
            ChatMember member = ChatMember.builder()
                    .chatId(CHAT_ID).memberId(ACTOR_ID).role(ChatMemberRole.ADMIN).build();

            when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(chat));
            when(chatMemberRepository.findById(new ChatMemberId(CHAT_ID, ACTOR_ID)))
                    .thenReturn(Optional.of(member));

            assertTrue(adapter.canSendMessage(CHAT_ID, ACTOR_ID));
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("ADAPTER-CAN-05: Should return true when chat is GROUP and actor is MEMBER")
        void canSendMessage_shouldReturnTrue_whenGroupMember() {
            Chat chat = Chat.builder().id(CHAT_ID).chatType(ChatType.GROUP).chatName("test").build();
            ChatMember member = ChatMember.builder()
                    .chatId(CHAT_ID).memberId(ACTOR_ID).role(ChatMemberRole.MEMBER).build();

            when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(chat));
            when(chatMemberRepository.findById(new ChatMemberId(CHAT_ID, ACTOR_ID)))
                    .thenReturn(Optional.of(member));

            assertTrue(adapter.canSendMessage(CHAT_ID, ACTOR_ID));
        }
    }

    @Nested
    class UpdateLastActivityTests {

        @Test
        @Tag("unit-messaging")
        @DisplayName("ADAPTER-UPD-01: Should save chat when found")
        void updateLastActivity_shouldSave_whenChatFound() {
            Chat chat = Chat.builder().id(CHAT_ID).chatType(ChatType.GROUP).chatName("test").build();

            when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.of(chat));

            adapter.updateLastActivity(CHAT_ID, Instant.now());

            verify(chatRepository).save(chat);
        }

        @Test
        @Tag("unit-messaging")
        @DisplayName("ADAPTER-UPD-02: Should not save when chat is not found")
        void updateLastActivity_shouldNotSave_whenChatNotFound() {
            when(chatRepository.findById(CHAT_ID)).thenReturn(Optional.empty());

            adapter.updateLastActivity(CHAT_ID, Instant.now());

            verify(chatRepository, never()).save(null);
        }
    }
}

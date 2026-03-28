package io.zalord.messaging.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.zalord.auth.domain.entities.User;
import io.zalord.common.exception.ChatNotFoundException;
import io.zalord.common.exception.UnauthorizedException;
import io.zalord.messaging.api.dto.CreateChatRequest;
import io.zalord.messaging.api.dto.UpdateChatRequest;
import io.zalord.messaging.domain.entities.Chat;
import io.zalord.messaging.domain.entities.ChatMember;
import io.zalord.messaging.domain.enums.ChatMemberRole;
import io.zalord.messaging.infrastructure.ChatMemberRepository;
import io.zalord.messaging.infrastructure.ChatRepository;
import jakarta.transaction.Transactional;

@Service
public class ChatService {
    private final ChatRepository chatRepository;

    private final ChatMemberRepository chatMemberRepository;

    public ChatService(ChatRepository _chatRepository, ChatMemberRepository _chatMemberRepository) {
        this.chatRepository = _chatRepository;
        this.chatMemberRepository = _chatMemberRepository;
    }

    @Transactional
    public Chat createChat(User user, CreateChatRequest request) {
        // Validation first

        // Logic execution here (assume all information at this point is 100% reliable)
        Chat newChat = new Chat();
        newChat.setChatName(request.getChatName());
        newChat.setChatType(request.getChatType());

        // Flush then update LastMessageAt to match chat creation time
        chatRepository.saveAndFlush(newChat);
        newChat.setLastActivityAt(newChat.getCreatedAt());

        // Following Messenger's approach of assigning all new members to be MEMBER role
        // and require explicit ADMIN assignments.
        List<ChatMember> members = new ArrayList<>();
        ChatMember owner = new ChatMember();
        owner.setChatId(newChat.getId());
        owner.setMemberId(user.getId());
        owner.setRole(ChatMemberRole.OWNER);
        members.add(owner);

        for (UUID memberId : request.getMemberIds()) {
            if (memberId.equals(owner.getMemberId()))
                continue;
            ChatMember newChatMember = new ChatMember();
            newChatMember.setChatId(newChat.getId());
            newChatMember.setMemberId(memberId);
            newChatMember.setRole(ChatMemberRole.MEMBER);
            members.add(newChatMember);
        }
        ;

        chatMemberRepository.saveAll(members);

        return newChat;
    }

    @Transactional
    public Chat updateChat(User user, UpdateChatRequest request) {
        // Check if the user is currently in the chat and is an admin/owner
        Chat chat = validateAndGetChat(
                        user,
                        request.getChatId(),
                        List.of(ChatMemberRole.ADMIN, ChatMemberRole.OWNER),
                    "Update chat");
        chat.setChatName(request.getChatName());
        // more in the future
        chatRepository.save(chat);
        return chat;
    }

    public void deleteChat(User user, UUID chatId) {
        // Check if the user is currently in the chat and is an admin/owner
        Chat chat = validateAndGetChat(
                user,
                chatId,
                List.of(ChatMemberRole.OWNER),
            "Delete chat");

        chatRepository.delete(chat);
    }

    public boolean assignChatAdmin(User user, UUID chatId, UUID memberId) {

    }

    private Chat validateAndGetChat(User user, UUID chatId, List<ChatMemberRole> roleList, String actionName) {
        Chat chat = chatRepository.findByChatIdAndDeletedAtIsNull(chatId)
            .orElseThrow(() -> new ChatNotFoundException("Chat not found."));

        boolean isAuthorized = chatMemberRepository.existsByChatIdAndMemberIdAndRoleInAndDeletedAtIsNull(
                chatId,
                user.getId(),
                roleList);

        if (!isAuthorized)
            throw new UnauthorizedException(actionName);

        return chat;
    }
}

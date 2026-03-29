package io.zalord.messaging.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.zalord.auth.domain.entities.User;
import io.zalord.common.exception.ChatNotFoundException;
import io.zalord.common.exception.MemberNotFound;
import io.zalord.common.exception.UnauthorizedException;
import io.zalord.messaging.api.dto.CreateChatRequest;
import io.zalord.messaging.api.dto.UpdateChatRequest;
import io.zalord.messaging.domain.entities.Chat;
import io.zalord.messaging.domain.entities.ChatMember;
import io.zalord.messaging.domain.entities.ChatMemberId;
import io.zalord.messaging.domain.enums.ChatMemberRole;
import io.zalord.messaging.domain.enums.ChatType;
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

    private record ChatContext(Chat chat, ChatMember member) {
    }

    @Transactional
    public Chat createChat(User user, CreateChatRequest request) {
        // Validation first

        // Logic execution here (assume all information at this point is 100% reliable)
        Chat newChat = new Chat();
        newChat.setChatName(request.getChatName());
        newChat.setChatType(request.getChatType());

        // Flush then update lastActivityAt to match chat creation time
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

        ChatContext ctx = getChatContext(
                user,
                request.getChatId());
        // Validate user's permission
        if (ctx.member().getRole() == ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Update chat");

        ctx.chat().setChatName(request.getChatName());
        // more in the future
        chatRepository.save(ctx.chat());
        return ctx.chat();
    }

    public void deleteChat(User user, UUID chatId) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                user,
                chatId);
        // Validate user's permission
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Delete chat");

        chatRepository.delete(ctx.chat());
    }

    public void assignChatAdmin(User user, UUID chatId, UUID memberId) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                user,
                chatId);
        // Validate user's permission
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Assign chat admin");

        chatMemberRepository.upsertAsRole(ctx.chat().getId(), memberId, ChatMemberRole.ADMIN.name());
    }

    public void removeChatAdmin(User user, UUID chatId, UUID memberId) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                user,
                chatId);
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Remove chat admin");

        chatMemberRepository.upsertAsRole(ctx.chat().getId(), memberId, ChatMemberRole.MEMBER.name());
    }

    public void leaveChat(User user, UUID chatId) {
        // Check if the user is currently in the chat and is an admin/owner
        ChatContext ctx = getChatContext(
                user,
                chatId);

        if (ctx.chat().getChatType() == ChatType.DIRECT) 
            throw new UnauthorizedException("Can't leave direct chat");

        ChatMemberRole actorRole = ctx.member().getRole();
        if (actorRole == ChatMemberRole.OWNER) {
            Optional<ChatMember> nextOwner = chatMemberRepository.findSecondMostSeniorMember(ctx.chat().getId());
            if (nextOwner.isEmpty()) {
                chatRepository.delete(ctx.chat());
                chatMemberRepository.delete(ctx.member());
                return;
            }
            else {
                nextOwner.get().setRole(ChatMemberRole.OWNER);
                chatMemberRepository.save(nextOwner.get());
                chatMemberRepository.delete(ctx.member());
                return;
            }
        }
        else chatMemberRepository.delete(ctx.member());
    }

    public void removeFromChat(User user, UUID chatId, UUID memberId) {
        // Check if the user is currently in the chat and is an admin/owner
        ChatContext ctx = getChatContext(
                user,
                chatId);

        if (user.getId().equals(memberId))
            throw new UnauthorizedException("Can't remove own account from chat.");

        ChatMember targetMember = chatMemberRepository.findById(new ChatMemberId(ctx.chat().getId(), memberId))
                .orElseThrow(() -> new MemberNotFound("Member not found in chat"));

        ChatMemberRole actorRole = ctx.member().getRole();

        if (actorRole == ChatMemberRole.ADMIN && targetMember.getRole() != ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Insufficient permissions");

        if (actorRole == ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Insufficient permissions");

        chatMemberRepository.delete(targetMember);
    }

    private ChatContext getChatContext(User user, UUID chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found."));

        ChatMember chatMember = chatMemberRepository.findById(new ChatMemberId(user.getId(), chatId))
                .orElseThrow(() -> new MemberNotFound("User is not in chat."));

        return new ChatContext(chat, chatMember);
    }
}

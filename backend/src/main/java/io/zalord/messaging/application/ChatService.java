package io.zalord.messaging.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.zalord.common.exception.ChatNotFoundException;
import io.zalord.common.exception.MemberNotFound;
import io.zalord.common.exception.UnauthorizedException;
import io.zalord.messaging.domain.entities.Chat;
import io.zalord.messaging.domain.entities.ChatMember;
import io.zalord.messaging.domain.entities.ChatMemberId;
import io.zalord.messaging.domain.enums.ChatMemberRole;
import io.zalord.messaging.domain.enums.ChatType;
import io.zalord.messaging.dto.request.CreateChatRequest;
import io.zalord.messaging.dto.request.UpdateChatRequest;
import io.zalord.messaging.dto.response.ChatResponse;
import io.zalord.messaging.repository.ChatMemberRepository;
import io.zalord.messaging.repository.ChatRepository;
import jakarta.transaction.Transactional;

@Service
public class ChatService {
    private final ChatRepository chatRepository;

    private final ChatMemberRepository chatMemberRepository;

    public ChatService(ChatRepository chatRepository, ChatMemberRepository chatMemberRepository) {
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
    }

    private record ChatContext(Chat chat, ChatMember member) {
    }

    @Transactional
    public ChatResponse createChat(UUID actorId, CreateChatRequest request) {
        // Validation first (idk why I wrote this, new chat shouldn't have validations
        // since they are new (duh))

        // Logic execution here (assume all information at this point is 100% reliable)
        Chat newChat = Chat.builder()
                .chatName(request.getChatName())
                .chatType(request.getChatType())
                .build();

        // Flush since we need chat to exist before adding members
        chatRepository.saveAndFlush(newChat);

        // Following Messenger's approach of assigning all new members to be MEMBER role
        // and require explicit ADMIN assignments.
        List<ChatMember> members = new ArrayList<>();
        ChatMember owner = ChatMember.builder()
                .chatId(newChat.getId())
                .memberId(actorId)
                .role(ChatMemberRole.OWNER)
                .build();
        members.add(owner);

        for (UUID memberId : request.getMemberIds()) {
            if (memberId.equals(owner.getMemberId()))
                continue;
            ChatMember newChatMember = ChatMember.builder()
                    .chatId(newChat.getId())
                    .memberId(memberId)
                    .role(ChatMemberRole.MEMBER)
                    .build();
            members.add(newChatMember);
        }
        ;

        chatMemberRepository.saveAll(members);

        return ChatResponse.builder()
                .id(newChat.getId())
                .chatName(newChat.getChatName())
                .chatType(newChat.getChatType())
                .lastActivityAt(newChat.getLastActivityAt())
                .build();
    }

    @Transactional
    public ChatResponse updateChat(UUID actorId, UpdateChatRequest request) {
        // Retrieve chat context
        ChatContext ctx = getChatContext(
                actorId,
                request.getChatId());
        // Validate user's permission
        if (ctx.member().getRole() == ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Update chat");

        ctx.chat().setChatName(request.getChatName());
        // more in the future
        chatRepository.save(ctx.chat());
        return ChatResponse.builder()
                .id(ctx.chat().getId())
                .chatName(ctx.chat().getChatName())
                .chatType(ctx.chat().getChatType())
                .lastActivityAt(ctx.chat().getLastActivityAt()).build();
    }

    @Transactional
    public void deleteChat(UUID actorId, UUID chatId) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                actorId,
                chatId);
        // Validate user's permission
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Delete chat");

        chatMemberRepository.softDeleteByChatId(ctx.chat().getId());
        chatRepository.delete(ctx.chat());
    }

    @Transactional
    public void promoteChatAdmin(UUID actorId, UUID chatId, UUID memberId) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                actorId,
                chatId);
        // Validate user's permission
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Assign chat admin");

        chatMemberRepository.upsertAsRole(ctx.chat().getId(), memberId, ChatMemberRole.ADMIN.name());
    }

    @Transactional
    public void transferChatOwnership(UUID actorId, UUID chatId, UUID memberId) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                actorId,
                chatId);
        // Validate user's permission
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Transfer chat ownership");

        chatMemberRepository.upsertAsRole(ctx.chat().getId(), ctx.member().getMemberId(), ChatMemberRole.ADMIN.name());
        chatMemberRepository.upsertAsRole(ctx.chat().getId(), memberId, ChatMemberRole.OWNER.name());
    }

    @Transactional
    public void demoteChatAdmin(UUID actorId, UUID chatId, UUID memberId) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                actorId,
                chatId);
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Remove chat admin");

        chatMemberRepository.upsertAsRole(ctx.chat().getId(), memberId, ChatMemberRole.MEMBER.name());
    }

    @Transactional
    public void leaveChat(UUID actorId, UUID chatId) {
        // Check if the user is currently in the chat and is an admin/owner
        ChatContext ctx = getChatContext(
                actorId,
                chatId);

        if (ctx.chat().getChatType() == ChatType.DIRECT)
            throw new UnauthorizedException("Can't leave direct chat");

        ChatMemberRole actorRole = ctx.member().getRole();
        if (actorRole == ChatMemberRole.OWNER) {
            Optional<ChatMember> nextOwner = chatMemberRepository.findSecondMostSeniorMember(ctx.chat().getId());
            if (nextOwner.isPresent()) {
                ChatMember newOwner = nextOwner.get();
                newOwner.setRole(ChatMemberRole.OWNER);
                chatMemberRepository.save(newOwner);
                chatMemberRepository.delete(ctx.member());
            } else {
                chatMemberRepository.softDeleteByChatId(chatId);
                chatRepository.delete(ctx.chat());
            }
        } else
            chatMemberRepository.delete(ctx.member());
    }

    @Transactional
    public void removeFromChat(UUID actorId, UUID chatId, UUID memberId) {
        if (actorId.equals(memberId))
            throw new UnauthorizedException("Can't remove own account from chat.");
        // Check if the user is currently in the chat and is an admin/owner
        ChatContext ctx = getChatContext(
                actorId,
                chatId);

        ChatMemberRole actorRole = ctx.member().getRole();
        
        if (actorRole == ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Insufficient permissions");
        
        ChatMember targetMember = chatMemberRepository.findById(new ChatMemberId(ctx.chat().getId(), memberId))
                .orElseThrow(() -> new MemberNotFound("Member not found in chat"));

        if (actorRole == ChatMemberRole.ADMIN && targetMember.getRole() != ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Insufficient permissions");

        chatMemberRepository.delete(targetMember);
    }

    private ChatContext getChatContext(UUID actorId, UUID chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found."));

        ChatMember chatMember = chatMemberRepository.findById(new ChatMemberId(chatId, actorId))
                .orElseThrow(() -> new MemberNotFound("User is not in chat."));

        return new ChatContext(chat, chatMember);
    }
}

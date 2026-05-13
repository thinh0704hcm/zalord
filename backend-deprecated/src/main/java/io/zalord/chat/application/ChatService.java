package io.zalord.chat.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import io.zalord.chat.application.commands.AddMemberCommand;

import io.zalord.chat.application.commands.CreateChatCommand;
import io.zalord.chat.application.commands.DeleteChatCommand;
import io.zalord.chat.application.commands.LeaveChatCommand;
import io.zalord.chat.application.commands.RemoveFromChatCommand;
import io.zalord.chat.application.commands.TransferChatOwnershipCommand;
import io.zalord.chat.application.commands.UpdateChatCommand;
import io.zalord.chat.application.commands.UpdateMemberRoleCommand;
import io.zalord.chat.domain.entities.Chat;
import io.zalord.chat.domain.entities.ChatMember;
import io.zalord.chat.domain.entities.ChatMemberId;
import io.zalord.chat.domain.enums.ChatMemberRole;
import io.zalord.chat.domain.enums.ChatType;
import io.zalord.chat.dto.response.ChatResponse;
import io.zalord.chat.repository.ChatMemberRepository;
import io.zalord.chat.repository.ChatRepository;
import io.zalord.common.exception.ChatNotFoundException;
import io.zalord.common.exception.MemberNotFound;
import io.zalord.common.exception.UnauthorizedException;
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
    public ChatResponse createChat(CreateChatCommand cmd) {
        // Validation first (idk why I wrote this, new chat shouldn't have validations
        // since they are new (duh))

        // Logic execution here (assume all information at this point is 100% reliable)
        Chat newChat = Chat.builder()
                .chatName(cmd.chatName())
                .chatType(cmd.chatType())
                .build();

        // Flush since we need chat to exist before adding members
        chatRepository.saveAndFlush(newChat);

        // Following Messenger's approach of assigning all new members to be MEMBER role
        // and require explicit ADMIN assignments.
        List<ChatMember> members = new ArrayList<>();
        ChatMember owner = ChatMember.builder()
                .chatId(newChat.getId())
                .memberId(cmd.actorId())
                .role(ChatMemberRole.OWNER)
                .build();
        members.add(owner);

        for (UUID memberId : cmd.memberIds()) {
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

        return new ChatResponse(
                newChat.getId(),
                newChat.getChatName(),
                newChat.getChatType(),
                newChat.getLastActivityAt());
    }

    @Transactional
    public ChatResponse updateChat(UpdateChatCommand cmd) {
        // Retrieve chat context
        ChatContext ctx = getChatContext(
                cmd.actorId(),
                cmd.chatId());
        // Validate user's permission
        if (ctx.member().getRole() == ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Update chat");

        ctx.chat().setChatName(cmd.chatName());
        // more in the future
        chatRepository.save(ctx.chat());
        return new ChatResponse(
                ctx.chat().getId(),
                ctx.chat().getChatName(),
                ctx.chat().getChatType(),
                ctx.chat().getLastActivityAt());
    }

    @Transactional
    public void deleteChat(DeleteChatCommand cmd) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                cmd.actorId(),
                cmd.chatId());
        // Validate user's permission
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Delete chat");

        chatMemberRepository.softDeleteByChatId(ctx.chat().getId());
        chatRepository.delete(ctx.chat());
    }

    @Transactional
    public void updateMemberRole(UpdateMemberRoleCommand cmd) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                cmd.actorId(),
                cmd.chatId());
        // Validate user's permission
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Assign chat admin");

        chatMemberRepository.upsertAsRole(ctx.chat().getId(), cmd.memberId(), cmd.role().toString());
    }

    @Transactional
    public void transferChatOwnership(TransferChatOwnershipCommand cmd) {
        // Check if the user is currently in the chat and is an owner
        ChatContext ctx = getChatContext(
                cmd.actorId(),
                cmd.chatId());
        // Validate user's permission
        if (ctx.member().getRole() != ChatMemberRole.OWNER)
            throw new UnauthorizedException("Transfer chat ownership");

        chatMemberRepository.upsertAsRole(ctx.chat().getId(), ctx.member().getMemberId(), ChatMemberRole.ADMIN.name());
        chatMemberRepository.upsertAsRole(ctx.chat().getId(), cmd.recipientId(), ChatMemberRole.OWNER.name());
    }

    @Transactional
    public void leaveChat(LeaveChatCommand cmd) {
        // Check if the user is currently in the chat and is an admin/owner
        ChatContext ctx = getChatContext(
                cmd.actorId(),
                cmd.chatId());

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
                chatMemberRepository.softDeleteByChatId(cmd.chatId());
                chatRepository.delete(ctx.chat());
            }
        } else
            chatMemberRepository.delete(ctx.member());
    }

    @Transactional
    public void removeFromChat(RemoveFromChatCommand cmd) {
        if (cmd.actorId().equals(cmd.memberId()))
            throw new UnauthorizedException("Can't remove own account from chat.");
        // Check if the user is currently in the chat and is an admin/owner
        ChatContext ctx = getChatContext(
                cmd.actorId(),
                cmd.chatId());

        ChatMemberRole actorRole = ctx.member().getRole();

        if (actorRole == ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Insufficient permissions");

        ChatMember targetMember = chatMemberRepository.findById(new ChatMemberId(ctx.chat().getId(), cmd.memberId()))
                .orElseThrow(() -> new MemberNotFound("Member not found in chat"));

        if (actorRole == ChatMemberRole.ADMIN && targetMember.getRole() != ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Insufficient permissions");

        chatMemberRepository.delete(targetMember);
    }

    public void validateCanSendMessage(UUID chatId, UUID actorId) {
        ChatContext ctx = getChatContext(actorId, chatId);

        if (ctx.chat().getChatType() == ChatType.COMMUNITY && ctx.member.getRole() == ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Send community message");
    }

    public void updateLastActivityAt(UUID chatId, Instant timestamp) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found with id" + chatId));
        chat.setLastActivityAt(timestamp);
        chatRepository.save(chat);
    }

    private ChatContext getChatContext(UUID actorId, UUID chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found."));

        ChatMember chatMember = chatMemberRepository.findById(new ChatMemberId(chatId, actorId))
                .orElseThrow(() -> new MemberNotFound("User is not in chat."));

        return new ChatContext(chat, chatMember);
    }

    public List<ChatResponse> getMyChats(UUID userId, Instant cursor, int size) {
        Slice<Chat> slice = chatRepository.findChatsBeforeCursor(userId, cursor, PageRequest.of(0, size));
        return slice.getContent().stream()
                .map(c -> new ChatResponse(c.getId(), c.getChatName(), c.getChatType(), c.getLastActivityAt()))
                .toList();
    }

    @Transactional
    public void addMember(AddMemberCommand cmd) {
        ChatContext ctx = getChatContext(cmd.actorId(), cmd.chatId());
        if (ctx.member().getRole() == ChatMemberRole.MEMBER)
            throw new io.zalord.common.exception.UnauthorizedException("Only OWNER or ADMIN can add members");
        chatMemberRepository.upsertAsRole(cmd.chatId(), cmd.memberId(), cmd.role().name());
    }
}

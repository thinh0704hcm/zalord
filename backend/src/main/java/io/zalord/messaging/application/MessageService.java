package io.zalord.messaging.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.zalord.common.exception.ChatNotFoundException;
import io.zalord.common.exception.MemberNotFound;
import io.zalord.common.exception.UnauthorizedException;
import io.zalord.messaging.api.dto.SendMessageRequest;
import io.zalord.messaging.api.dto.MessageResponse;
import io.zalord.messaging.domain.entities.Chat;
import io.zalord.messaging.domain.entities.ChatMember;
import io.zalord.messaging.domain.entities.ChatMemberId;
import io.zalord.messaging.domain.entities.Message;
import io.zalord.messaging.domain.enums.ChatMemberRole;
import io.zalord.messaging.domain.enums.ChatType;
import io.zalord.messaging.infrastructure.ChatMemberRepository;
import io.zalord.messaging.infrastructure.ChatRepository;
import io.zalord.messaging.infrastructure.MessageRepository;

@Service
public class MessageService {
    private ChatRepository chatRepository;
    private ChatMemberRepository chatMemberRepository;
    private MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository, ChatRepository chatRepository,
            ChatMemberRepository chatMemberRepository) {
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
    }

    public MessageResponse sendMessage(UUID actorId, SendMessageRequest request) {
        Chat chat = chatRepository.findById(request.getChatId())
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));

        ChatMember chatMember = chatMemberRepository
                .findById(new ChatMemberId(request.getChatId(), actorId))
                .orElseThrow(() -> new MemberNotFound("User not found in chat"));


        if (chat.getChatType() == ChatType.COMMUNITY && chatMember.getRole() == ChatMemberRole.MEMBER)
            throw new UnauthorizedException("Insufficient permissions");

        Message message = Message.builder()
                .chatId(chatMember.getChatId())
                .senderId(chatMember.getMemberId())
                .contentType(request.getContentType())
                .payload(request.getPayload())
                .createdAt(Instant.now())
                .build();
        messageRepository.save(message);

        chat.setLastActivityAt(Instant.now());
        chatRepository.save(chat);

        return MessageResponse.builder()
                .chatId(message.getChatId())
                .senderId(message.getSenderId())
                .createdAt(message.getCreatedAt())
                .build();
    }
}

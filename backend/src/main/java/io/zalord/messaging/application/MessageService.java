package io.zalord.messaging.application;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.zalord.common.exception.ChatNotFoundException;
import io.zalord.common.exception.MemberNotFound;
import io.zalord.common.exception.MessageNotFoundException;
import io.zalord.common.exception.UnauthorizedException;
import io.zalord.messaging.application.commands.DeleteMessageCommand;
import io.zalord.messaging.application.commands.SendMessageCommand;
import io.zalord.messaging.domain.entities.Chat;
import io.zalord.messaging.domain.entities.ChatMember;
import io.zalord.messaging.domain.entities.ChatMemberId;
import io.zalord.messaging.domain.entities.Message;
import io.zalord.messaging.domain.enums.ChatMemberRole;
import io.zalord.messaging.domain.enums.ChatType;
import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.dto.response.MessageResponse;
import io.zalord.messaging.repository.ChatMemberRepository;
import io.zalord.messaging.repository.ChatRepository;
import io.zalord.messaging.repository.MessageRepository;
import jakarta.transaction.Transactional;

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

        @Transactional
        public MessageResponse sendMessage(SendMessageCommand cmd) {
                Instant timestamp = Instant.now();
                Chat chat = chatRepository.findById(cmd.chatId())
                                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));

                ChatMember chatMember = chatMemberRepository
                                .findById(new ChatMemberId(cmd.chatId(), cmd.actorId()))
                                .orElseThrow(() -> new MemberNotFound("User not found in chat"));

                if (chat.getChatType() == ChatType.COMMUNITY && chatMember.getRole() == ChatMemberRole.MEMBER)
                        throw new UnauthorizedException("Insufficient permissions");

                Message message = Message.builder()
                                .chatId(chatMember.getChatId())
                                .senderId(chatMember.getMemberId())
                                .contentType(cmd.contentType())
                                .payload(cmd.payload())
                                .createdAt(timestamp)
                                .build();
                messageRepository.save(message);

                chat.setLastActivityAt(timestamp);
                chatRepository.save(chat);

                return new MessageResponse(
                                message.getId(),
                                message.getChatId(),
                                message.getSenderId(),
                                message.getContentType(),
                                message.getPayload(),
                                message.getCreatedAt());
        }

        @Transactional
        public MessageResponse deleteMessage(DeleteMessageCommand cmd) {
                Message message = messageRepository.findById(cmd.messageId())
                                .orElseThrow(() -> new MessageNotFoundException("Message not found"));
                if (cmd.actorId() != message.getSenderId())
                        throw new UnauthorizedException("Delete message");
                message.setContentType(ContentType.DELETED);
                message.setPayload(null);

                return new MessageResponse(
                                message.getId(),
                                message.getChatId(),
                                message.getSenderId(),
                                message.getContentType(),
                                message.getPayload(),
                                message.getCreatedAt());

        }

}

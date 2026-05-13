package io.zalord.messaging.application;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.zalord.common.exception.MessageNotFoundException;
import io.zalord.common.exception.UnauthorizedException;
import io.zalord.messaging.application.commands.DeleteMessageCommand;
import io.zalord.messaging.application.commands.SendMessageCommand;
import io.zalord.messaging.domain.entities.Message;
import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.dto.response.MessageResponse;
import io.zalord.messaging.port.ChatAccessPort;
import io.zalord.messaging.repository.MessageRepository;
import jakarta.transaction.Transactional;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatAccessPort chatAccessPort;

    public MessageService(MessageRepository messageRepository, ChatAccessPort chatAccessPort) {
        this.messageRepository = messageRepository;
        this.chatAccessPort = chatAccessPort;
    }

    @Transactional
    public MessageResponse sendMessage(SendMessageCommand cmd) {
        Instant timestamp = Instant.now();

        if (!chatAccessPort.canSendMessage(cmd.chatId(), cmd.actorId()))
            throw new UnauthorizedException("Insufficient permissions");

        Message message = Message.builder()
                .chatId(cmd.chatId())
                .senderId(cmd.actorId())
                .contentType(cmd.contentType())
                .payload(cmd.payload())
                .createdAt(timestamp)
                .build();
        messageRepository.save(message);

        chatAccessPort.updateLastActivity(cmd.chatId(), timestamp);

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

        if (!cmd.actorId().equals(message.getSenderId()))
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

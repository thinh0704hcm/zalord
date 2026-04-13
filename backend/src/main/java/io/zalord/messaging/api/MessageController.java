package io.zalord.messaging.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.zalord.common.exception.UnauthorizedException;
import io.zalord.common.security.AuthenticatedUser;
import io.zalord.messaging.application.MessageService;
import io.zalord.messaging.application.commands.DeleteMessageCommand;
import io.zalord.messaging.domain.entities.Message;
import io.zalord.messaging.dto.response.MessageResponse;
import io.zalord.messaging.port.ChatAccessPort;
import io.zalord.messaging.repository.MessageRepository;

@RestController
@RequestMapping("/api/chats")
public class MessageController {

    private final MessageService messageService;
    private final MessageRepository messageRepository;
    private final ChatAccessPort chatAccessPort;

    public MessageController(MessageService messageService, MessageRepository messageRepository, ChatAccessPort chatAccessPort) {
        this.messageService = messageService;
        this.messageRepository = messageRepository;
        this.chatAccessPort = chatAccessPort;
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID chatId,
            @RequestParam(defaultValue = "9999-12-31T23:59:59Z") Instant cursor,
            @RequestParam(defaultValue = "30") int size) {
        if (!chatAccessPort.canSendMessage(chatId, actor.userId()))
            throw new UnauthorizedException("Not a member of this chat");
        Slice<Message> slice = messageRepository.findByChatIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                chatId, cursor, PageRequest.of(0, size));
        List<MessageResponse> body = slice.getContent().stream()
                .map(m -> new MessageResponse(m.getId(), m.getChatId(), m.getSenderId(),
                        m.getContentType(), m.getPayload(), m.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponse> deleteMessage(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID messageId) {
        return ResponseEntity.ok(messageService.deleteMessage(new DeleteMessageCommand(actor.userId(), messageId)));
    }
}

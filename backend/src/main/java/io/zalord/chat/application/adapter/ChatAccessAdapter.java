package io.zalord.chat.application.adapter;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.zalord.chat.application.ChatService;
import io.zalord.messaging.application.port.ChatAccessPort;

@Component
public class ChatAccessAdapter implements ChatAccessPort {
    private final ChatService chatService;

    public ChatAccessAdapter(ChatService chatService) {
        this.chatService = chatService;
    }

    public void validateCanSendMessage(UUID chatId, UUID actorId) {
        chatService.validateCanSendMessage(chatId, actorId);
    }

    public void updateLastActivityAt(UUID chatId, Instant timestamp) {
        chatService.updateLastActivityAt(chatId, timestamp);
    }
}

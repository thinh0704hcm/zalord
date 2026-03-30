package io.zalord.messaging.application;

import io.zalord.messaging.api.dto.CreateMessageRequest;
import io.zalord.messaging.infrastructure.MessageRepository;

public class MessageService {
    private MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public MessageResponse createMessage(CreateMessageRequest request) {
        
    }
}

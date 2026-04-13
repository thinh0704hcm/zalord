package io.zalord.messaging.api;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import io.zalord.common.exception.UnauthorizedException;
import io.zalord.common.security.AuthenticatedUser;
import io.zalord.messaging.application.MessageService;
import io.zalord.messaging.application.commands.SendMessageCommand;
import io.zalord.messaging.dto.request.WsSendMessageRequest;
import io.zalord.messaging.dto.response.MessageResponse;

@Controller
public class ChatWebSocketController {

    private final MessageService messageService;

    public ChatWebSocketController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/chat/{chatId}/send")
    @SendTo("/topic/chats/{chatId}")
    public MessageResponse sendMessage(
            @DestinationVariable UUID chatId,
            @Payload WsSendMessageRequest request,
            Principal principal) {

        AuthenticatedUser user = extractUser(principal);
        return messageService.sendMessage(
                new SendMessageCommand(user.userId(), chatId, request.contentType(), request.payload()));
    }

    private AuthenticatedUser extractUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth &&
                auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new UnauthorizedException("WebSocket authentication required");
    }
}

package io.zalord.presence.application;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import io.zalord.common.security.AuthenticatedUser;

@Component
public class StompPresenceEventListener {

    private static final String CHAT_TOPIC_PREFIX = "/topic/chats/";

    private final PresenceService presenceService;
    private final PresenceSessionRegistry registry;

    public StompPresenceEventListener(PresenceService presenceService, PresenceSessionRegistry registry) {
        this.presenceService = presenceService;
        this.registry = registry;
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        UUID userId = extractUserId(accessor.getUser());
        if (sessionId != null && userId != null) {
            registry.registerSession(sessionId, userId);
        }
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();

        if (destination == null || sessionId == null) return;
        if (!destination.startsWith(CHAT_TOPIC_PREFIX)) return;

        try {
            UUID chatId = UUID.fromString(destination.substring(CHAT_TOPIC_PREFIX.length()));
            UUID userId = registry.getUserId(sessionId);
            if (userId != null) {
                registry.addChatSubscription(sessionId, chatId);
                presenceService.userJoinedChat(chatId, userId);
            }
        } catch (IllegalArgumentException ignored) {
            // destination does not end with a valid UUID — not a chat topic
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) return;

        UUID userId = registry.getUserId(sessionId);
        Set<UUID> chatIds = registry.removeSession(sessionId);

        if (userId != null) {
            for (UUID chatId : chatIds) {
                presenceService.userLeftChat(chatId, userId);
            }
        }
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth &&
                auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        return null;
    }
}

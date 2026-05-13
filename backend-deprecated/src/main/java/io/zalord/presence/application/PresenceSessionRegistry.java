package io.zalord.presence.application;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory session registry mapping STOMP session IDs to user + subscribed chats.
 *
 * Stage 1 thesis note: this works because all WebSocket connections share the same JVM.
 * In Stage 2, multiple Push Service instances cannot share in-memory state — this
 * registry must be replaced with Redis, and disconnect events must be routed via
 * Redis Pub/Sub to the correct instance.
 */
@Component
public class PresenceSessionRegistry {

    private record SessionInfo(UUID userId, Set<UUID> chatIds) {}

    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, UUID userId) {
        sessions.put(sessionId, new SessionInfo(userId, ConcurrentHashMap.newKeySet()));
    }

    public void addChatSubscription(String sessionId, UUID chatId) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) {
            info.chatIds().add(chatId);
        }
    }

    public UUID getUserId(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        return info != null ? info.userId() : null;
    }

    public Set<UUID> removeSession(String sessionId) {
        SessionInfo info = sessions.remove(sessionId);
        return info != null ? info.chatIds() : Set.of();
    }
}

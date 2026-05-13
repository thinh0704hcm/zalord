package io.zalord.presence.application;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {

    private static final String KEY_PREFIX = "presence:chat:";

    private final StringRedisTemplate redis;

    public PresenceService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void userJoinedChat(UUID chatId, UUID userId) {
        redis.opsForSet().add(key(chatId), userId.toString());
    }

    public void userLeftChat(UUID chatId, UUID userId) {
        redis.opsForSet().remove(key(chatId), userId.toString());
    }

    public Set<UUID> getOnlineMembers(UUID chatId) {
        Set<String> members = redis.opsForSet().members(key(chatId));
        if (members == null) return Set.of();
        return members.stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    private String key(UUID chatId) {
        return KEY_PREFIX + chatId;
    }
}

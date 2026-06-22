package zalord.message_service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Membership cache in Redis. media-service reads this to authorize attachment
 * downloads without RPC-ing message-service. Cache key:
 *
 *   conv:{conversationId}:members  →  SET<userId-as-string>
 *
 * Writes happen here in message-service (single source of truth) when
 * ConversationMembers change — both the DIRECT init path and the GROUP
 * projection path. Idempotent: SADD/SREM repeated are no-ops.
 *
 * Eviction: SREM on member removal; DEL whole key when the conversation is
 * destroyed (not currently exposed via API, but the method is here for future
 * delete-group support).
 */
@Component
@Slf4j
public class ConversationMembersCache {

    private final StringRedisTemplate redis;

    public ConversationMembersCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void addMember(UUID conversationId, UUID userId) {
        try {
            redis.opsForSet().add(key(conversationId), userId.toString());
        } catch (Exception ex) {
            // Don't fail the calling tx on Redis hiccups. Members can still
            // be authoritatively read from Postgres if needed. Just log loud.
            log.warn("Redis SADD failed conv={} user={}: {}", conversationId, userId, ex.getMessage());
        }
    }

    public void addMembers(UUID conversationId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        String[] ids = userIds.stream().map(UUID::toString).toArray(String[]::new);
        try {
            redis.opsForSet().add(key(conversationId), ids);
        } catch (Exception ex) {
            log.warn("Redis SADD bulk failed conv={}: {}", conversationId, ex.getMessage());
        }
    }

    public void removeMember(UUID conversationId, UUID userId) {
        try {
            redis.opsForSet().remove(key(conversationId), userId.toString());
        } catch (Exception ex) {
            log.warn("Redis SREM failed conv={} user={}: {}", conversationId, userId, ex.getMessage());
        }
    }

    public void evictConversation(UUID conversationId) {
        try {
            redis.delete(key(conversationId));
        } catch (Exception ex) {
            log.warn("Redis DEL failed conv={}: {}", conversationId, ex.getMessage());
        }
    }

    /** For debugging / health endpoints. */
    public Set<String> getMembers(UUID conversationId) {
        Set<String> raw = redis.opsForSet().members(key(conversationId));
        return raw == null ? Set.of() : raw.stream().collect(Collectors.toSet());
    }

    private static String key(UUID conversationId) {
        return "conv:" + conversationId + ":members";
    }
}

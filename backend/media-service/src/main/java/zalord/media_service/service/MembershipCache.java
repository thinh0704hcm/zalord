package zalord.media_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Read-only view over the conv:{id}:members Redis set that message-service
 * maintains. Microsecond-fast membership check that lets media-service authz
 * ATTACHMENT downloads without calling message-service over HTTP/gRPC.
 *
 * Trade-off — eventual consistency: a user removed from a group might briefly
 * still pass this check (until message-service's GroupEventConsumer processes
 * the member.removed event and SREMs the key). Acceptable for media access.
 */
@Component
@Slf4j
public class MembershipCache {

    private final StringRedisTemplate redis;

    public MembershipCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isMember(UUID conversationId, UUID userId) {
        try {
            Boolean result = redis.opsForSet().isMember(key(conversationId), userId.toString());
            return Boolean.TRUE.equals(result);
        } catch (Exception ex) {
            // Redis hiccup → fail closed (deny). Better to 503 / 403 than to
            // accidentally hand out URLs without authz.
            log.warn("Redis SISMEMBER failed conv={} user={}: {}", conversationId, userId, ex.getMessage());
            return false;
        }
    }

    private static String key(UUID conversationId) {
        return "conv:" + conversationId + ":members";
    }
}

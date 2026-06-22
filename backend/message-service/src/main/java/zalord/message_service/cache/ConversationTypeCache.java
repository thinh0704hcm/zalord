package zalord.message_service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import zalord.message_service.enums.ConversationType;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cache for conversation type (DIRECT | GROUP). Type is immutable after a
 * conversation is created, so this cache never needs to handle updates — only
 * eviction on conversation delete (which isn't exposed today).
 *
 * Hot path: InboxProjector fires this on every message.created event to decide
 * whether to populate other_user_id.
 *
 *   conv:{conversationId}:type  →  "DIRECT" | "GROUP"
 */
@Component
@Slf4j
public class ConversationTypeCache {

    private final StringRedisTemplate redis;

    public ConversationTypeCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Read-through. Loader is only invoked on cache miss. */
    public ConversationType getOrLoad(UUID conversationId, Supplier<ConversationType> loader) {
        String k = key(conversationId);
        try {
            String cached = redis.opsForValue().get(k);
            if (cached != null) return ConversationType.valueOf(cached);
        } catch (Exception ex) {
            log.warn("ConversationTypeCache GET failed conv={}: {}", conversationId, ex.getMessage());
        }

        ConversationType loaded = loader.get();
        if (loaded != null) {
            try {
                // No TTL — type is immutable for the lifetime of the conversation.
                redis.opsForValue().set(k, loaded.name());
            } catch (Exception ex) {
                log.warn("ConversationTypeCache SET failed conv={}: {}", conversationId, ex.getMessage());
            }
        }
        return loaded;
    }

    public void evict(UUID conversationId) {
        try {
            redis.delete(key(conversationId));
        } catch (Exception ex) {
            log.warn("ConversationTypeCache DEL failed conv={}: {}", conversationId, ex.getMessage());
        }
    }

    private static String key(UUID id) {
        return "conv:" + id + ":type";
    }
}

package zalord.media_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import zalord.media_service.model.Media;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-through cache for media metadata. Skips the per-id Postgres lookup that
 * ValidateAttachments + downloadUrl + get/metadata would otherwise do on every
 * call. Snapshot is enough for those paths — only the GET /media/{id} REST
 * response also needs sizeBytes/mimeType/timestamps, and that one stays on the
 * DB (less hot).
 *
 * Invariant: after a media row reaches READY or DELETED it never mutates
 * again, so cache lifetime is bounded only by TTL.
 */
@Component
@Slf4j
public class MediaCache {

    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public MediaCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public record Snapshot(
            UUID id,
            UUID ownerId,
            UUID conversationId,
            String kind,    // MediaKind name
            String status   // MediaStatus name
    ) {
        public static Snapshot of(Media m) {
            return new Snapshot(
                    m.getId(),
                    m.getOwnerId(),
                    m.getConversationId(),
                    m.getKind().name(),
                    m.getStatus().name());
        }
    }

    public Map<UUID, Snapshot> getMany(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        List<UUID> ordered = List.copyOf(ids);
        List<String> keys = ordered.stream().map(MediaCache::key).toList();
        List<String> raws;
        try {
            raws = redis.opsForValue().multiGet(keys);
        } catch (Exception ex) {
            log.warn("MediaCache MGET failed, falling back to DB: {}", ex.getMessage());
            return Collections.emptyMap();
        }
        if (raws == null) return Collections.emptyMap();

        Map<UUID, Snapshot> out = new LinkedHashMap<>(raws.size());
        for (int i = 0; i < raws.size(); i++) {
            String raw = raws.get(i);
            if (raw == null) continue;
            try {
                out.put(ordered.get(i), json.readValue(raw, Snapshot.class));
            } catch (Exception ex) {
                log.warn("MediaCache parse failed id={}, evicting: {}", ordered.get(i), ex.getMessage());
                evict(ordered.get(i));
            }
        }
        return out;
    }

    public void putAll(Collection<Snapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return;
        Map<String, String> batch = new HashMap<>(snapshots.size());
        for (Snapshot s : snapshots) {
            try {
                batch.put(key(s.id()), json.writeValueAsString(s));
            } catch (Exception ex) {
                log.warn("MediaCache serialize failed id={}: {}", s.id(), ex.getMessage());
            }
        }
        if (batch.isEmpty()) return;
        try {
            redis.opsForValue().multiSet(batch);
            for (String k : batch.keySet()) redis.expire(k, TTL);
        } catch (Exception ex) {
            log.warn("MediaCache MSET failed: {}", ex.getMessage());
        }
    }

    public void put(Snapshot s) {
        if (s == null) return;
        try {
            redis.opsForValue().set(key(s.id()), json.writeValueAsString(s), TTL);
        } catch (Exception ex) {
            log.warn("MediaCache SET failed id={}: {}", s.id(), ex.getMessage());
        }
    }

    public void evict(UUID id) {
        try {
            redis.delete(key(id));
        } catch (Exception ex) {
            log.warn("MediaCache DEL failed id={}: {}", id, ex.getMessage());
        }
    }

    private static String key(UUID id) {
        return "media:" + id;
    }
}

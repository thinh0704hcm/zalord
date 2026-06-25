package zalord.media_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.media_service.enums.MediaStatus;
import zalord.media_service.model.Media;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    /** Bulk fetch by ids — used by the ValidateAttachments gRPC. */
    List<Media> findByIdIn(Collection<UUID> ids);
    List<Media> findByConversationIdAndStatusOrderByCreatedAtDesc(UUID conversationId, MediaStatus status);

    /**
     * Native INSERT with explicit id — service needs to control the id so the
     * storage_key (which embeds the id) matches the row before the bytes are
     * uploaded. JPA's save() with @GeneratedValue + pre-set id triggers
     * "Detached entity passed to persist", so bypass JPA for this one insert.
     */
    @Modifying
    @Query(value = """
        INSERT INTO media (id, owner_id, kind, conversation_id, storage_key, file_name, mime_type, status, created_at)
        VALUES (:id, :ownerId, :kind, :conversationId, :storageKey, :fileName, :mimeType, 'PENDING', now())
        """, nativeQuery = true)
    void insertNew(@Param("id") UUID id,
                   @Param("ownerId") UUID ownerId,
                   @Param("kind") String kind,
                   @Param("conversationId") UUID conversationId,
                   @Param("storageKey") String storageKey,
                   @Param("fileName") String fileName,
                   @Param("mimeType") String mimeType);
}

package zalord.media_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import zalord.media_service.enums.MediaKind;
import zalord.media_service.enums.MediaStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "media")
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaKind kind;

    /** Only set for ATTACHMENT; null for AVATAR. */
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaStatus status = MediaStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}

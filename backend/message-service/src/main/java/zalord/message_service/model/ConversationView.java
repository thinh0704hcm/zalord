package zalord.message_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * CQRS read model: one row per (user, conversation) with denormalized cache
 * of the last message + unread count. Built by InboxProjector consuming
 * message.created events. GET /api/v1/inbox reads from here with a single
 * indexed query — no JOIN, no cross-table aggregation.
 *
 * Uses a surrogate UUID id as PK with a UNIQUE (user_id, conversation_id)
 * constraint for natural-key dedupe — consistent with the other entities and
 * avoiding @EmbeddedId boilerplate.
 */
@Entity
@Getter
@Setter
@Table(
        name = "conversation_views",
        uniqueConstraints = @UniqueConstraint(
                name = "conv_views_unique",
                columnNames = {"user_id", "conversation_id"}
        )
)
public class ConversationView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "other_user_id")
    private UUID otherUserId;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_sender_id")
    private UUID lastSenderId;

    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount = 0;


    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

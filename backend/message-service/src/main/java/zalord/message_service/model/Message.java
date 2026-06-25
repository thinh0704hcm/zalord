package zalord.message_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Recall ("thu hồi"): non-null means the sender retracted this message.
    // The row stays so pagination + reply-quote snapshots don't break.
    @Column(name = "recalled_at")
    private Instant recalledAt;

    // Reply snapshot — denormalized so quotes survive recall of the original.
    @Column(name = "reply_to_message_id")
    private UUID replyToMessageId;

    @Column(name = "reply_to_sender_id")
    private UUID replyToSenderId;

    @Column(name = "reply_to_preview", length = 200)
    private String replyToPreview;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}

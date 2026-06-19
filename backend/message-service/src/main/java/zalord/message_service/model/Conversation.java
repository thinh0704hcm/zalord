package zalord.message_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import zalord.message_service.enums.ConversationType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}

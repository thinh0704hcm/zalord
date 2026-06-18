package zalord.auth_service.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name="phone_number", nullable = false, unique = true)
    private String phoneNumber;

    @Column(name="password_hash", nullable = false)
    private String passwordHash;

    @Column(name="created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name="deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}

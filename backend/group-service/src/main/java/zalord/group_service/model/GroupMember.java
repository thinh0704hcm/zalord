package zalord.group_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import zalord.group_service.enums.MemberRole;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(
        name = "group_members",
        uniqueConstraints = @UniqueConstraint(
                name = "group_members_unique",
                columnNames = {"group_id", "user_id"}
        )
)
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = Instant.now();
    }
}

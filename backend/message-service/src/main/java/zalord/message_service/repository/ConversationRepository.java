package zalord.message_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.message_service.model.Conversation;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Native INSERT for projecting a conversation with a pre-supplied id
     * (the group.id from group-service). Bypasses Hibernate's @GeneratedValue
     * UUID flow, which throws "Detached entity passed to persist" when an id
     * is set on a new entity with a generator. ON CONFLICT makes it idempotent
     * (event re-delivery is safe).
     */
    @Modifying
    @Query(value = """
        INSERT INTO conversations (id, type, created_at)
        VALUES (:id, :type, now())
        ON CONFLICT (id) DO NOTHING
        """, nativeQuery = true)
    void insertWithExplicitId(@Param("id") UUID id, @Param("type") String type);
}

package zalord.message_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.message_service.model.ConversationView;

import java.time.Instant;
import java.util.UUID;

public interface ConversationViewRepository extends JpaRepository<ConversationView, UUID> {

    Page<ConversationView> findByUserIdOrderByLastMessageAtDesc(UUID userId, Pageable pageable);

    /**
     * UPSERT on the (user_id, conversation_id) UNIQUE constraint. PostgreSQL
     * ON CONFLICT works with any UNIQUE constraint, not just PK, so the
     * surrogate-key PK doesn't affect this.
     */
    @Modifying
    @Query(value = """
        INSERT INTO conversation_views
            (user_id, conversation_id, other_user_id, last_message_preview,
             last_message_at, last_sender_id, unread_count, updated_at)
        VALUES (:userId, :conversationId, :otherUserId, :preview,
                :messageAt, :senderId,
                CASE WHEN :userId = :senderId THEN 0 ELSE 1 END,
                now())
        ON CONFLICT (user_id, conversation_id) DO UPDATE SET
            last_message_preview = EXCLUDED.last_message_preview,
            last_message_at      = EXCLUDED.last_message_at,
            last_sender_id       = EXCLUDED.last_sender_id,
            unread_count         = CASE
                WHEN EXCLUDED.last_sender_id = conversation_views.user_id THEN 0
                ELSE conversation_views.unread_count + 1
            END,
            updated_at           = now()
        """, nativeQuery = true)
    void upsertOnNewMessage(@Param("userId") UUID userId,
                            @Param("conversationId") UUID conversationId,
                            @Param("otherUserId") UUID otherUserId,
                            @Param("preview") String preview,
                            @Param("messageAt") Instant messageAt,
                            @Param("senderId") UUID senderId);

    @Modifying
    @Query(value = """
        INSERT INTO conversation_views
            (user_id, conversation_id, other_user_id, unread_count, updated_at)
        VALUES (:userId, :conversationId, :otherUserId, 0, now())
        ON CONFLICT (user_id, conversation_id) DO NOTHING
        """, nativeQuery = true)
    void initView(@Param("userId") UUID userId,
                  @Param("conversationId") UUID conversationId,
                  @Param("otherUserId") UUID otherUserId);

    @Modifying
    @Query("UPDATE ConversationView v " +
           "SET v.unreadCount = 0, " +
           "    v.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE v.userId = :userId AND v.conversationId = :conversationId")
    int markRead(@Param("userId") UUID userId,
                 @Param("conversationId") UUID conversationId);

    @Modifying
    @Query("DELETE FROM ConversationView v WHERE v.userId = :userId AND v.conversationId = :conversationId")
    int deleteUserConversationView(@Param("userId") UUID userId, @Param("conversationId") UUID conversationId);
}

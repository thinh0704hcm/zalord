package zalord.message_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.message_service.model.MessageReadReceipt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageReadReceiptRepository extends JpaRepository<MessageReadReceipt, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO message_read_receipts (message_id, conversation_id, user_id, read_at)
        SELECT m.id, m.conversation_id, :userId, :readAt
        FROM messages m
        WHERE m.conversation_id = :conversationId
          AND m.created_at <= :upToCreatedAt
          AND m.sender_id <> :userId
        ON CONFLICT (message_id, user_id) DO NOTHING
        """, nativeQuery = true)
    int insertFirstReadsUpTo(@Param("userId") UUID userId,
                             @Param("conversationId") UUID conversationId,
                             @Param("upToCreatedAt") Instant upToCreatedAt,
                             @Param("readAt") Instant readAt);

    @Query("SELECT r FROM MessageReadReceipt r " +
           "WHERE r.messageId = :messageId " +
           "AND r.userId <> :callerUserId " +
           "AND r.userId <> :senderId " +
           "ORDER BY r.readAt DESC")
    List<MessageReadReceipt> findReadersOfMessage(@Param("messageId") UUID messageId,
                                                   @Param("callerUserId") UUID callerUserId,
                                                   @Param("senderId") UUID senderId);
}

package zalord.message_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.message_service.model.MessageAttachment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, MessageAttachment.Id> {

    @Query("SELECT a FROM MessageAttachment a WHERE a.id.messageId = :messageId ORDER BY a.position ASC")
    List<MessageAttachment> findByMessageIdOrderByPosition(@Param("messageId") UUID messageId);

    @Query("SELECT a FROM MessageAttachment a WHERE a.id.messageId IN :messageIds ORDER BY a.id.messageId, a.position ASC")
    List<MessageAttachment> findByMessageIdIn(@Param("messageIds") Collection<UUID> messageIds);
}

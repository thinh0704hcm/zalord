package zalord.message_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.message_service.model.ConversationMember;
import zalord.message_service.model.ConversationMemberId;

import java.util.List;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    boolean existsByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    List<ConversationMember> findAllByIdConversationId(UUID conversationId);

    @Query("SELECT cm.id.conversationId FROM ConversationMember cm WHERE cm.id.userId = :userId")
    Page<UUID> findConversationIdsByUserId(@Param("userId") UUID userId, Pageable pageable);
}

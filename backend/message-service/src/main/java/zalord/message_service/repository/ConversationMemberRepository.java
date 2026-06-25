package zalord.message_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.message_service.model.ConversationMember;

import java.util.List;
import java.util.UUID;

import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

    Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ConversationMember> findAllByConversationId(UUID conversationId);

    @Query("SELECT cm.conversationId FROM ConversationMember cm WHERE cm.userId = :userId")
    Page<UUID> findConversationIdsByUserId(@Param("userId") UUID userId, Pageable pageable);
}

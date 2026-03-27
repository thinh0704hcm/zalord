package io.zalord.messaging.infrastructure;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.zalord.messaging.domain.entities.ChatMember;
import io.zalord.messaging.domain.entities.ChatMemberId;
import io.zalord.messaging.domain.enums.ChatMemberRole;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {
    boolean existsByChatIdAndMemberIdAndDeletedAtIsNull(UUID chatId, UUID memberId);

    boolean existsByChatIdAndMemberIdAndRoleInAndDeletedAtIsNull(
        UUID chatId,
        UUID memberId,
        Collection<ChatMemberRole> roles
    );

    
}

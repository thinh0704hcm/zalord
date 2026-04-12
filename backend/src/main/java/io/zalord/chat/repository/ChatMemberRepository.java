package io.zalord.chat.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.zalord.chat.domain.entities.ChatMember;
import io.zalord.chat.domain.entities.ChatMemberId;
import io.zalord.chat.domain.enums.ChatMemberRole;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {
    boolean existsByChatIdAndMemberId(UUID chatId, UUID memberId);

    boolean existsByChatIdAndMemberIdAndRoleIn(
            UUID chatId,
            UUID memberId,
            Collection<ChatMemberRole> roles);

        @Query(value = """
            SELECT * FROM messaging.chat_members
            WHERE chat_id = :chatId
            ORDER BY CASE role
                WHEN 'OWNER' THEN 0
                WHEN 'ADMIN' THEN 1
                WHEN 'MEMBER' THEN 2
            END
            LIMIT 1 
            OFFSET 1
            """, nativeQuery = true)
    Optional<ChatMember> findSecondMostSeniorMember(@Param("chatId") UUID chatId);

    @Modifying                                                                                                                                 
    @Query(value = "UPDATE messaging.chat_members SET deleted_at = NOW() WHERE chat_id = :chatId", nativeQuery = true)
    void softDeleteByChatId(@Param("chatId") UUID chatId);

    @Modifying
    @Query(value = """
            INSERT INTO messaging.chat_members (chat_id, member_id, role)
            VALUES (:chatId, :memberId, :role)
            ON CONFLICT (chat_id, member_id)
            DO UPDATE SET role = :role, deleted_at = NULL
            """, nativeQuery = true)
    void upsertAsRole(@Param("chatId") UUID chatId, @Param("memberId") UUID memberId, @Param("role") String role);

}

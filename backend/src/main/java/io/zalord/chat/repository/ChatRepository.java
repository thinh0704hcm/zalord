package io.zalord.chat.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.zalord.chat.domain.entities.Chat;
import io.zalord.chat.domain.enums.ChatType;

public interface ChatRepository extends JpaRepository<Chat, UUID> {
    @Query("""
        SELECT c FROM Chat c
        JOIN ChatMember cm on cm.chatId = c.id
        WHERE cm.memberId = :memberId
            AND c.lastActivityAt < :cursor
        ORDER BY c.lastActivityAt DESC
    """)
    Slice<Chat> findChatsBeforeCursor(
        UUID memberId,
        Instant cursor,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Chat c
        JOIN ChatMember cm on cm.chatId = c.id
        WHERE cm.memberId = :memberId
            AND c.chatType = :chatType
            AND c.lastActivityAt < :cursor
        ORDER BY c.lastActivityAt DESC
    """)
    Slice<Chat> findChatsBeforeCursor(
        UUID memberId,
        ChatType chatType,
        Instant cursor,
        Pageable pageable
    );
}

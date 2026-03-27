package io.zalord.messaging.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.data.autoconfigure.web.DataWebProperties.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.zalord.messaging.domain.entities.Chat;
import io.zalord.messaging.domain.enums.ChatType;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    Optional<Chat> findByChatIdAndDeletedAtIsNull (UUID chatId);

    @Query("""
        SELECT c FROM Chat c
        JOIN ChatMember cm on cm.chatId = c.id
        WHERE cm.memberId = :memberId
            AND c.lastMessageAt < :cursor
        ORDER BY c.lastMessageAt DESC
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
            AND c.lastMessageAt < :cursor
        ORDER BY c.lastMessageAt DESC
    """)
    Slice<Chat> findChatsBeforeCursor(
        UUID memberId,
        ChatType chatType,
        Instant cursor,
        Pageable pageable
    );
}

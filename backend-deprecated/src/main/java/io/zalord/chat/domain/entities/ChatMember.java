package io.zalord.chat.domain.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import io.zalord.chat.domain.enums.ChatMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE messaging.chat_members SET deleted_at = NOW() WHERE chat_id = ? AND member_id = ?")
@IdClass(ChatMemberId.class)
@Table(name = "chat_members", schema = "chat")
public class ChatMember {
    @Id
    @Column(name = "chat_id", updatable = false)
    private UUID chatId;

    @Id
    @Column(name = "member_id", updatable = false)
    private UUID memberId;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private ChatMemberRole role;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}

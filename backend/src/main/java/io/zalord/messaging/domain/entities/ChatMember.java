package io.zalord.messaging.domain.entities;

import java.util.UUID;

import io.zalord.messaging.domain.enums.ChatMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@IdClass(ChatMemberId.class)
@Table(name = "chat_members", schema = "messaging")
public class ChatMember {
    @Id
    private UUID chatId;

    @Id
    private UUID memberId;

    @Column(name = "chat_member_role", nullable = false)
    @Enumerated(EnumType.STRING)
    private ChatMemberRole role;
}

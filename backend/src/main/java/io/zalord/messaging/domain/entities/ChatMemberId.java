package io.zalord.messaging.domain.entities;

import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@AllArgsConstructor
public class ChatMemberId {
    private UUID chatId;
    private UUID memberId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMemberId)) return false;
        ChatMemberId id = (ChatMemberId) o;
        return this.chatId.equals(id.chatId) && this.memberId.equals(id.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId, memberId);
    }
}

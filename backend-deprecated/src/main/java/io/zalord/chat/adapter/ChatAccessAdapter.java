package io.zalord.chat.adapter;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.zalord.chat.domain.entities.Chat;
import io.zalord.chat.domain.entities.ChatMember;
import io.zalord.chat.domain.entities.ChatMemberId;
import io.zalord.chat.domain.enums.ChatMemberRole;
import io.zalord.chat.domain.enums.ChatType;
import io.zalord.chat.repository.ChatMemberRepository;
import io.zalord.chat.repository.ChatRepository;
import io.zalord.messaging.port.ChatAccessPort;

@Service
public class ChatAccessAdapter implements ChatAccessPort {

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;

    public ChatAccessAdapter(ChatRepository chatRepository, ChatMemberRepository chatMemberRepository) {
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
    }

    @Override
    public boolean canSendMessage(UUID chatId, UUID actorId) {
        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (chat == null) return false;

        ChatMember member = chatMemberRepository.findById(new ChatMemberId(chatId, actorId)).orElse(null);
        if (member == null) return false;

        if (chat.getChatType() == ChatType.COMMUNITY && member.getRole() == ChatMemberRole.MEMBER)
            return false;

        return true;
    }

    @Override
    public void updateLastActivity(UUID chatId, Instant timestamp) {
        chatRepository.findById(chatId).ifPresent(chat -> {
            chat.setLastActivityAt(timestamp);
            chatRepository.save(chat);
        });
    }
}

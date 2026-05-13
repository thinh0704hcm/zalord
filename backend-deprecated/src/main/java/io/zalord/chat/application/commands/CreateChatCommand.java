package io.zalord.chat.application.commands;

import java.util.Set;
import java.util.UUID;

import io.zalord.chat.domain.enums.ChatType;

public record CreateChatCommand(
        UUID actorId,
        String chatName,
        ChatType chatType,
        Set<UUID> memberIds) {
}
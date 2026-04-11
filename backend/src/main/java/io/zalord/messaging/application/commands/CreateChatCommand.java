package io.zalord.messaging.application.commands;

import java.util.Set;
import java.util.UUID;

import io.zalord.messaging.domain.enums.ChatType;

public record CreateChatCommand(
        UUID actorId,
        String chatName,
        ChatType chatType,
        Set<UUID> memberIds) {
}
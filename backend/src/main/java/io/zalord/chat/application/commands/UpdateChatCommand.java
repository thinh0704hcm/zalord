package io.zalord.chat.application.commands;

import java.util.UUID;

public record UpdateChatCommand(
        UUID actorId,
        UUID chatId,
        String chatName
) {}

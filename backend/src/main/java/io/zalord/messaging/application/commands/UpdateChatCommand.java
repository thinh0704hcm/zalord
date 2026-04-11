package io.zalord.messaging.application.commands;

import java.util.UUID;

public record UpdateChatCommand(
        UUID actorId,
        UUID chatId,
        String chatName
) {}

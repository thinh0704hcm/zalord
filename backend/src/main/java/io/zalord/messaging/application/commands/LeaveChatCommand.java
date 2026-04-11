package io.zalord.messaging.application.commands;

import java.util.UUID;

public record LeaveChatCommand(UUID actorId, UUID chatId) {

}

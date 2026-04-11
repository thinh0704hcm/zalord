package io.zalord.messaging.application.commands;

import java.util.UUID;

public record DeleteChatCommand(UUID actorId, UUID chatId) {

}

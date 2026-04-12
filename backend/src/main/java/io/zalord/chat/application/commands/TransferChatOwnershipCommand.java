package io.zalord.chat.application.commands;

import java.util.UUID;

public record TransferChatOwnershipCommand(UUID actorId, UUID chatId, UUID recipientId) {

}

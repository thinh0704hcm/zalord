package io.zalord.chat.application.commands;

import java.util.UUID;

public record RemoveFromChatCommand(UUID actorId, UUID chatId, UUID memberId) {

}

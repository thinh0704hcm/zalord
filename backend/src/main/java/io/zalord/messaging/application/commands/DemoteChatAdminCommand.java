package io.zalord.messaging.application.commands;

import java.util.UUID;

public record DemoteChatAdminCommand(UUID actorId, UUID chatId, UUID memberId) {

}

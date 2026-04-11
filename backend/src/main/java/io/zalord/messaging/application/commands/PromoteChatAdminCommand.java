package io.zalord.messaging.application.commands;

import java.util.UUID;

public record PromoteChatAdminCommand(UUID actorId, UUID chatId, UUID memberId) {

}

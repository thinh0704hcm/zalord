package io.zalord.messaging.application.commands;

import java.util.UUID;

public record DeleteMessageCommand(UUID actorId, UUID messageId) {

}

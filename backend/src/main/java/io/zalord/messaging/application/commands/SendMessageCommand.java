package io.zalord.messaging.application.commands;

import java.util.UUID;

import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.domain.interfaces.MessagePayload;

public record SendMessageCommand(UUID actorId, UUID chatId, ContentType contentType, MessagePayload payload) {

}

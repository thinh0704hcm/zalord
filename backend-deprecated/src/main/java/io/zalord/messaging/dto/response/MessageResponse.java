package io.zalord.messaging.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.domain.interfaces.MessagePayload;

public record MessageResponse(UUID id,
        UUID chatId,
        UUID senderId,
        ContentType contentType,
        MessagePayload payload,
        Instant createdAt) {
}

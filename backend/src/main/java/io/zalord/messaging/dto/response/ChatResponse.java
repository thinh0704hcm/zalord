package io.zalord.messaging.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.zalord.messaging.domain.enums.ChatType;

public record ChatResponse(UUID id, String chatName, ChatType chatType, Instant lastActivityAt) {
}

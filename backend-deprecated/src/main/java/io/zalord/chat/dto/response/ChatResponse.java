package io.zalord.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.zalord.chat.domain.enums.ChatType;

public record ChatResponse(UUID id, String chatName, ChatType chatType, Instant lastActivityAt) {
}

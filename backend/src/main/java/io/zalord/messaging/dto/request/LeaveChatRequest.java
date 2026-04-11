package io.zalord.messaging.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public class LeaveChatRequest {
    @NotNull
    private UUID chatId;
}

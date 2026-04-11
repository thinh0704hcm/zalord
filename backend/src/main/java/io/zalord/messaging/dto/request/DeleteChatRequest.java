package io.zalord.messaging.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public class DeleteChatRequest {
    @NotNull
    private UUID chatId;
}

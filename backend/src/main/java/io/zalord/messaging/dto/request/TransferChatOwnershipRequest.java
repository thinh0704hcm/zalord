package io.zalord.messaging.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public class TransferChatOwnershipRequest {
    @NotNull
    private UUID chatId;
    @NotNull
    private UUID recipientId;
}

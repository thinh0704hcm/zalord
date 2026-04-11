package io.zalord.messaging.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record TransferChatOwnershipRequest(@NotNull UUID recipientId) {}

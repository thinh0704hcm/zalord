package io.zalord.chat.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateChatRequest(
    @NotNull UUID chatId,
    @NotBlank String chatName
) {}

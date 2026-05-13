package io.zalord.chat.dto.request;

import java.util.Set;
import java.util.UUID;

import io.zalord.chat.domain.enums.ChatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateChatRequest(
    @NotBlank String chatName,
    @NotNull ChatType chatType,
    @NotEmpty Set<@NotNull UUID> memberIds
) {}

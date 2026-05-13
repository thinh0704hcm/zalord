package io.zalord.chat.dto.request;

import java.util.UUID;

import io.zalord.chat.domain.enums.ChatMemberRole;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(@NotNull UUID memberId, @NotNull ChatMemberRole role) {}

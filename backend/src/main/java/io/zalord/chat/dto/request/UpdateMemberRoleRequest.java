package io.zalord.chat.dto.request;

import io.zalord.chat.domain.enums.ChatMemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull ChatMemberRole role) {}

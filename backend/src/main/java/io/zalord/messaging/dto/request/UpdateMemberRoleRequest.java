package io.zalord.messaging.dto.request;

import io.zalord.messaging.domain.enums.ChatMemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull ChatMemberRole role) {}

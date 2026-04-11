package io.zalord.messaging.application.commands;

import java.util.UUID;

import io.zalord.messaging.domain.enums.ChatMemberRole;

public record UpdateMemberRoleCommand(UUID actorId, UUID chatId, UUID memberId, ChatMemberRole role) {

}

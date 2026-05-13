package io.zalord.chat.application.commands;

import java.util.UUID;

import io.zalord.chat.domain.enums.ChatMemberRole;

public record AddMemberCommand(UUID actorId, UUID chatId, UUID memberId, ChatMemberRole role) {}

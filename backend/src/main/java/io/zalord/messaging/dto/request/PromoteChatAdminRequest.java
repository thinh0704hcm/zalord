package io.zalord.messaging.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public class PromoteChatAdminRequest {
    @NotNull
    private UUID chatId;
    
    @NotNull
    private UUID memberId;
}

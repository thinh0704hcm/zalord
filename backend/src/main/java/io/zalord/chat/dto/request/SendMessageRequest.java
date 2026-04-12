package io.zalord.chat.dto.request;

import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.domain.interfaces.MessagePayload;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(
    @NotNull ContentType contentType,
    @NotNull MessagePayload payload
) {}

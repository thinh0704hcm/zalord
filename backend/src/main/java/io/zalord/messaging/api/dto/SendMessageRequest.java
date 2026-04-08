package io.zalord.messaging.api.dto;

import java.util.UUID;

import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.domain.interfaces.MessagePayload;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {
    @NotNull
    private UUID chatId;
    @NotNull
    private ContentType contentType;
    @NotNull
    private MessagePayload payload;
}

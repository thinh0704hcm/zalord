package io.zalord.messaging.api.dto;

import java.util.UUID;

import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.domain.interfaces.MessagePayload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMessageRequest {
    @NotBlank
    private UUID chatId;
    @NotBlank
    private UUID senderId;
    @NotNull
    private ContentType contentType;
    @NotNull
    private MessagePayload payload;
}

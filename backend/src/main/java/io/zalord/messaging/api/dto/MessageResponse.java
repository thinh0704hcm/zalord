package io.zalord.messaging.api.dto;

import java.time.Instant;
import java.util.UUID;

import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.domain.interfaces.MessagePayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class MessageResponse {
    private UUID id;
    private UUID chatId;
    private UUID senderId;
    private ContentType contentType;
    private MessagePayload payload;
    private Instant createdAt;
}

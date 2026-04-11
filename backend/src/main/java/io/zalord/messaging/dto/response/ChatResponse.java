package io.zalord.messaging.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.zalord.messaging.domain.enums.ChatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ChatResponse {
    private UUID id;
    private String chatName;
    private ChatType chatType;
    private Instant lastActivityAt;
}

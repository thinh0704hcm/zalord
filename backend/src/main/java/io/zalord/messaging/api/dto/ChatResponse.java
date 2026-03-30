package io.zalord.messaging.api.dto;

import java.time.Instant;

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
    private String chatName;
    private ChatType chatType;
    private Instant lastActivityAt;
}

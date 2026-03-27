package io.zalord.messaging.api.dto;

import java.util.UUID;

import io.zalord.messaging.domain.enums.ChatType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeChatTypeRequest {
    private UUID chatId;

    private ChatType chatType;
}

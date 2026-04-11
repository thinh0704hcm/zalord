package io.zalord.messaging.dto.request;

import java.util.UUID;

import io.zalord.messaging.domain.enums.ChatType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateChatRequest {
    @NotNull
    private UUID chatId;
    
    private String chatName;
    private ChatType chatType;
}

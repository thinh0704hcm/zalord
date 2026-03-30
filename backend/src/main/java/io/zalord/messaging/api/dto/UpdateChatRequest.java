package io.zalord.messaging.api.dto;

import java.util.UUID;

import io.zalord.messaging.domain.enums.ChatType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateChatRequest {
    @NotBlank
    private UUID chatId;
    
    private String chatName;
    private ChatType chatType;
}

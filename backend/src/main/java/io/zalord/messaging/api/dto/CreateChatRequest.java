package io.zalord.messaging.api.dto;

import java.util.Set;
import java.util.UUID;

import io.zalord.messaging.domain.enums.ChatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChatRequest {
    @NotBlank
    private String chatName;

    @NotNull
    private ChatType chatType;

    @NotEmpty
    private Set<@NotNull UUID> memberIds;
}

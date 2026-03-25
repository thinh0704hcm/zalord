package io.zalord.auth.api.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private UUID userId;
    private String fullName;
    private String phoneNumber;
}

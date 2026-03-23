package io.zalord.auth.api;

import java.util.UUID;

public class AuthResponse {
    private String accessToken;
    private UUID userId;
    private String fullName;
    private String phoneNumber;

    public AuthResponse(String token, UUID id, String fullName, String phoneNumber) {
        this.accessToken = token;
        this.userId = id;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
    }

    public String getAccessToken() {
        return accessToken;
    }
}

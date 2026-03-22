package io.giano.auth.application;

import java.util.UUID;

public class AuthResponse {
    public String accessToken;
    public UUID userId;
    public String fullName;
    public String phoneNumber;

    public AuthResponse(String token, UUID id, String fullName, String phoneNumber) {
        this.accessToken = token;
        this.userId = id;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
    }
}

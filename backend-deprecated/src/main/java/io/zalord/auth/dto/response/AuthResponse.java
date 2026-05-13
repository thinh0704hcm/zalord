package io.zalord.auth.dto.response;

import java.util.UUID;

public record AuthResponse(
        String token,
        String refreshToken,
        UUID userId,
        String phoneNumber,
        String email) {}

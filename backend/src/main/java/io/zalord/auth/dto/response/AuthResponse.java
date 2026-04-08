package io.zalord.auth.dto.response;

import java.util.UUID;

public record AuthResponse (
    String accessToken,
    UUID userId,
    String fullName,
    String phoneNumber
) {}

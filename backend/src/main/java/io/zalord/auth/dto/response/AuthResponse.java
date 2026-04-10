package io.zalord.auth.dto.response;

import java.util.UUID;

public record AuthResponse (
    String token,
    UUID userId,
    String phoneNumber,
    String email
) {}

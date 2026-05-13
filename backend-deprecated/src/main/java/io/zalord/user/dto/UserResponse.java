package io.zalord.user.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String phoneNumber,
        String email,
        String fullName,
        LocalDate birthDate,
        String gender,
        Instant createdAt) {}

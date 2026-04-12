package io.zalord.common.events;

import java.time.LocalDate;
import java.util.UUID;

public record UserRegisteredEvent(
    UUID id,
    String phoneNumber,
    String email,
    String fullName,
    LocalDate birthDate,
    String gender
) {}

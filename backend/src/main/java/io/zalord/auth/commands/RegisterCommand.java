package io.zalord.auth.commands;

import java.time.LocalDate;

public record RegisterCommand(
    String phoneNumber,
    String password,
    String fullName,
    String email,
    LocalDate birthDate,
    String gender
) {}

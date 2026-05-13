package io.zalord.auth.application.commands;

public record RegisterCommand(
    String phoneNumber,
    String password,
    String email
) {}

package io.zalord.common.events;

import java.util.UUID;

public record AccountRegisteredEvent(
    UUID userId,
    String phoneNumber,
    String email
) {}

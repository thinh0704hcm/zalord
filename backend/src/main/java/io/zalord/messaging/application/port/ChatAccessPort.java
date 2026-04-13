package io.zalord.messaging.application.port;

import java.time.Instant;
import java.util.UUID;

public interface ChatAccessPort {
    void validateCanSendMessage(UUID chatId, UUID actorId);
    void updateLastActivityAt(UUID chatId, Instant timestamp);
}

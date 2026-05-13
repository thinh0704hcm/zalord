package io.zalord.messaging.port;

import java.time.Instant;
import java.util.UUID;

public interface ChatAccessPort {
    boolean canSendMessage(UUID chatId, UUID actorId);
    void updateLastActivity(UUID chatId, Instant timestamp);
}

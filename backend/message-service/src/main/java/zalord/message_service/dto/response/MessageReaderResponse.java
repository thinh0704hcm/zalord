package zalord.message_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MessageReaderResponse(
        UUID userId,
        Instant readAt
) {
}

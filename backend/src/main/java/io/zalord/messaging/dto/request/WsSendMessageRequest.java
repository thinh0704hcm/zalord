package io.zalord.messaging.dto.request;

import io.zalord.messaging.domain.enums.ContentType;
import io.zalord.messaging.domain.interfaces.MessagePayload;

public record WsSendMessageRequest(ContentType contentType, MessagePayload payload) {}

package io.zalord.common.exception;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {
    private final String error;
    private final String code;
    private final Instant timestamp;
    private final Map<String, String> fields;
}

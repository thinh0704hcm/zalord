package io.zalord.common.exception;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {
    private final String error;
    private final String code;
    private final Instant timestamp;
}

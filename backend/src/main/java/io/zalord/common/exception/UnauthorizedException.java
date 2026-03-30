package io.zalord.common.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String action) {
        super(action);
    }
}

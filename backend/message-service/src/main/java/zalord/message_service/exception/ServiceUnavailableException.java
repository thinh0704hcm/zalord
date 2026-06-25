package zalord.message_service.exception;

/**
 * Thrown when a downstream dependency (media-service gRPC, Redis, etc.) is
 * unreachable for long enough that the circuit breaker has opened. We surface
 * 503 so the client knows to retry later instead of treating it as a permanent
 * 400/500.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

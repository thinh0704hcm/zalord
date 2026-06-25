package zalord.auth_service.exception;

/**
 * Thrown when a downstream sync dependency (currently: user-service gRPC) is
 * unreachable long enough that the circuit breaker has opened. Maps to 503 so
 * the client retries instead of treating it as a permanent failure.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

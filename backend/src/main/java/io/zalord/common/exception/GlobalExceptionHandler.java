package io.zalord.common.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler( {InvalidCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        ErrorResponse body = ErrorResponse.builder()
            .error(e.getMessage())
            .code("INVALID_CREDENTAILS")
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler( {UserAlreadyExistsException.class})
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException e) {
        ErrorResponse body = ErrorResponse.builder()
            .error(e.getMessage())
            .code("USER_ALREADY_EXISTS")
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler ({Exception.class})
    public ResponseEntity<ErrorResponse> handleUnwantedException(Exception e) {
        e.printStackTrace();
        ErrorResponse body = ErrorResponse.builder()
            .error(e.getMessage())
            .code("UNDEFINED")
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(500).body(body);
    }
}

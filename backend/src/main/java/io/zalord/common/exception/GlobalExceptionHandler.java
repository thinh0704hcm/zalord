package io.zalord.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler( {InvalidCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        ErrorResponse body = ErrorResponse.builder()
            .error("Invalid credentials.")
            .code("INVALID_CREDENTIALS")
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler( {UserAlreadyExistsException.class})
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException e) {
        ErrorResponse body = ErrorResponse.builder()
            .error("User already exists.")
            .code("USER_ALREADY_EXISTS")
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler ({EmailAlreadyExistsException.class})
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException e) {
        ErrorResponse body = ErrorResponse.builder()
            .error("Email already exists.")
            .code("EMAIL_ALREADY_EXISTS")
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler ({UnauthorizedException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        ErrorResponse body = ErrorResponse.builder()
            .error("User is unauthorized to perform action:" + e.getMessage())
            .code("UNAUTHORIZED")
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler ({ChatNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleChatNotFound(ChatNotFoundException e) {
        ErrorResponse body = ErrorResponse.builder()
            .error("Chat not found")
            .code("CHAT_NOT_FOUND")
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler ({MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException e) {
        Map<String, String> fields = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage,
                (first, second) -> first,
                LinkedHashMap::new));
        
        ErrorResponse body = ErrorResponse.builder()
            .error("Validation failed")
            .code("VALIDATION_FAILED")
            .timestamp(Instant.now())
            .fields(fields)
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
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

package zalord.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zalord.auth_service.model.ApiResponse;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler  {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleUserNotFoundException(UserNotFoundException ex) {
        ApiResponse<?> response = new ApiResponse<>(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                null,
                "USER_NOT_FOUND"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidCredentialsException(InvalidCredentialsException ex) {
        ApiResponse<?> response = new ApiResponse<>(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage(),
                null,
                "INVALID_CREDENTIALS"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(PhoneNumberAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<?>> handlePhoneNumberAlreadyExistsException(PhoneNumberAlreadyExistsException ex) {
        ApiResponse<?> response = new ApiResponse<>(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                null,
                "PHONE_NUMBER_ALREADY_EXISTS"
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ApiResponse<?> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST,
                message,
                null,
                "VALIDATION_ERROR"
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex) {
        ApiResponse<?> apiResponse = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred: " + ex.getMessage(),
                null,
                "INTERNAL_SERVER_ERROR"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(apiResponse);
    }
}

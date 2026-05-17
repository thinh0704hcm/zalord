package zalord.auth_service.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class ApiResponse<T> {

    private String status;
    private String message;
    private T data;
    private String errorCode;
    private Instant timestamp;

    public ApiResponse(HttpStatus status, String message, T data, String errorCode) {
        this.status = status.is2xxSuccessful() ? "success" : "error"; // 200 -> 209 is success
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
        this.timestamp = Instant.now();
    }
}

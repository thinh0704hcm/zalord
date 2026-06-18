package zalord.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Phone number is required")
        @Size(min = 10, max = 10, message = "Phone number must be 10 digits")
        String phoneNumber,

        @NotBlank(message = "Password is required")
        String password
) {
}

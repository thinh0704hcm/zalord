package io.zalord.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank
    @Pattern(regexp = "\\d{10,}", message = "phoneNumber must contain at least 10 digits")
    private String phoneNumber;
    
    @Size(min = 6)
    @NotBlank
    private String password;
}

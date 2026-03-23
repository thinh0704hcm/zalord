package io.zalord.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @Size(min = 10, max = 10)
    @NotBlank
    private String phoneNumber;
    @Size(min = 6)
    private String password;
}

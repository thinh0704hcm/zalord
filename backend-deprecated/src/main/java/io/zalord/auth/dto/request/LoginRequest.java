package io.zalord.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(

    @Pattern(regexp = "\\d{10,}", message = "phoneNumber must contain at least 10 digits")
    @NotBlank
    String phoneNumber,
    
    @Size(min = 6)
    @NotBlank
    String password
) {}

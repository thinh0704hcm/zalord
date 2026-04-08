package io.zalord.auth.dto.request;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    
    @Pattern(regexp = "\\d{10,}", message = "phoneNumber must contain at least 10 digits")
    @NotBlank
    String phoneNumber,

    @Size(min = 6)
    @NotBlank
    String password,

    @NotBlank
    String fullName,

    @Email
    String email,

    @DateTimeFormat
    LocalDate birthDate,

    String gender
) {}
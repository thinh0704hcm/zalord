package io.zalord.auth.api.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    
    @Pattern(regexp = "\\d{10,}", message = "phoneNumber must contain at least 10 digits")
    @NotBlank
    private String phoneNumber;

    @Size(min = 6)
    @NotBlank
    private String password;

    @NotBlank
    private String fullName;

    @Email
    private String email;

    @DateTimeFormat
    private LocalDate birthDate;

    private String gender;
}

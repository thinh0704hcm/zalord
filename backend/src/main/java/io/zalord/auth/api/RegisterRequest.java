package io.zalord.auth.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @Size(min = 10)
    private String phoneNumber;

    @Size(min = 6)
    private String password;

    @NotBlank
    private String fullName;

    @Email
    private String email;

    @DateTimeFormat
    private LocalDate birthDate;

    private String gender;
}

package io.giano.auth.application;

import java.time.LocalDate;

public class SignUpRequest {
    public String phoneNumber;
    public String password;
    public String fullName;
    public LocalDate birthDate;
    public String gender;
}

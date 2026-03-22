package io.giano.auth.api;

public class LoginRequest {
    private String phoneNumber;
    private String password;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPassword() {
        return password;
    }
}

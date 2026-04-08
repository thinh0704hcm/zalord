package io.zalord.auth.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.zalord.auth.application.AuthService;
import io.zalord.auth.commands.RegisterCommand;
import io.zalord.auth.dto.request.LoginRequest;
import io.zalord.auth.dto.request.RegisterRequest;
import io.zalord.auth.dto.response.AuthResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService _authService) {
        this.authService = _authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {

        AuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        RegisterCommand cmd = new RegisterCommand(
            registerRequest.phoneNumber(),
            registerRequest.password(),
            registerRequest.fullName(),
            registerRequest.email(),
            registerRequest.birthDate(),
            registerRequest.gender()
        );

        AuthResponse response = authService.register(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
package zalord.auth_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zalord.auth_service.dto.request.LoginRequest;
import zalord.auth_service.dto.request.RegisterRequest;
import zalord.auth_service.dto.response.LoginResponse;
import zalord.auth_service.dto.response.RegisterResponse;
import zalord.auth_service.model.ApiResponse;
import zalord.auth_service.service.IAuthService;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration and login")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user with phone number and password")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse data = authService.register(
                request.displayName(),
                request.phoneNumber(),
                request.password()
        );

        ApiResponse<RegisterResponse> response = new ApiResponse<>(
                HttpStatus.CREATED,
                "User registered successfully",
                data,
                null
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/create-admin")
    @Operation(summary = "Create an admin user (bootstrap / admin-only endpoint)")
    public ResponseEntity<ApiResponse<RegisterResponse>> createAdmin(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse data = authService.createAdmin(
                request.displayName(),
                request.phoneNumber(),
                request.password()
        );

        ApiResponse<RegisterResponse> response = new ApiResponse<>(
                HttpStatus.CREATED,
                "Admin created successfully",
                data,
                null
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with phone number and password, returns JWT")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse data = authService.login(request.phoneNumber(), request.password());

        ApiResponse<LoginResponse> response = new ApiResponse<>(
                HttpStatus.OK,
                "Login successful",
                data,
                null
        );

        return ResponseEntity.ok(response);
    }
}

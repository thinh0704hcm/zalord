package zalord.auth_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zalord.auth_service.dto.request.LoginRequest;
import zalord.auth_service.dto.request.LogoutRequest;
import zalord.auth_service.dto.request.RefreshRequest;
import zalord.auth_service.dto.request.RegisterRequest;
import zalord.auth_service.dto.response.LoginResponse;
import zalord.auth_service.dto.response.RefreshResponse;
import zalord.auth_service.dto.response.RegisterResponse;
import zalord.auth_service.enums.RoleName;
import zalord.auth_service.exception.ForbiddenException;
import zalord.auth_service.model.ApiResponse;
import zalord.auth_service.service.IAuthService;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Operation(summary = "Create an admin user (ADMIN role required)")
    public ResponseEntity<ApiResponse<RegisterResponse>> createAdmin(
            // Hidden from Swagger: Kong injects this from the validated JWT and
            // wipes any client-supplied value, so a text box here would mislead.
            @Parameter(hidden = true)
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @Valid @RequestBody RegisterRequest request) {

        requireRole(rolesHeader, RoleName.ADMIN);

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

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse data = authService.refresh(request.refreshToken());

        ApiResponse<RefreshResponse> response = new ApiResponse<>(
                HttpStatus.OK,
                "Token refreshed",
                data,
                null
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token (logout this session)")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());

        ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.OK,
                "Logged out",
                null,
                null
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Authorize from the X-User-Roles header that Kong injects (comma-separated,
     * derived from the validated JWT's `roles` claim). auth-service never parses
     * the access token itself — Kong already verified it.
     */
    private void requireRole(String rolesHeader, RoleName required) {
        Set<String> roles = rolesHeader == null || rolesHeader.isBlank()
                ? Set.of()
                : Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .collect(Collectors.toSet());

        if (!roles.contains(required.name())) {
            throw new ForbiddenException(required.name() + " role required");
        }
    }
}

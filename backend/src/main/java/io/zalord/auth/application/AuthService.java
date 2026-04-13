package io.zalord.auth.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.zalord.auth.application.commands.RegisterCommand;
import io.zalord.auth.dto.request.LoginRequest;
import io.zalord.auth.dto.response.AuthResponse;
import io.zalord.auth.domain.entities.Credential;
import io.zalord.auth.repository.CredentialRepository;
import io.zalord.common.events.UserRegisteredEvent;
import io.zalord.common.exception.EmailAlreadyExistsException;
import io.zalord.common.exception.InvalidCredentialsException;
import io.zalord.common.exception.UserAlreadyExistsException;
import io.zalord.common.security.JwtService;
import jakarta.transaction.Transactional;

@Service
public class AuthService {

    private final CredentialRepository credentialRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenService refreshTokenService;

    public AuthService(CredentialRepository credentialRepository, JwtService jwtService,
            PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher,
            RefreshTokenService refreshTokenService) {
        this.credentialRepository = credentialRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse login(LoginRequest loginRequest) {
        Optional<Credential> storedCredential = credentialRepository.findByPhoneNumber(loginRequest.phoneNumber());
        if (storedCredential.isEmpty()) {
            throw new InvalidCredentialsException("Invalid credentials.");
        }

        Credential credential = storedCredential.get();
        if (!passwordEncoder.matches(loginRequest.password(), credential.getPasswordHash()))
            throw new InvalidCredentialsException("Invalid credentials.");

        Map<String,Object> claims = Map.of(
            "userId", credential.getUserId(),
            "phoneNumber", credential.getPhoneNumber()
        );

        return new AuthResponse(
            jwtService.generateToken(credential.getUserId(), claims),
            refreshTokenService.issue(credential.getUserId()),
            credential.getUserId(),
            credential.getPhoneNumber(),
            credential.getEmail()
        );
    }

    @Transactional
    public AuthResponse register(RegisterCommand cmd) {
        if (credentialRepository.existsByPhoneNumber(cmd.phoneNumber()))
            throw new UserAlreadyExistsException("Phone number is already registered.");

        if (cmd.email() != null && credentialRepository.existsByEmail(cmd.email()))
            throw new EmailAlreadyExistsException("Email already exists.");

        UUID userId = UUID.randomUUID();
        Credential credential = new Credential();
        credential.setUserId(userId);
        credential.setPhoneNumber(cmd.phoneNumber());
        credential.setEmail(cmd.email());
        credential.setPasswordHash(passwordEncoder.encode(cmd.password()));
        credential.setActive(true);
        credentialRepository.save(credential);

        eventPublisher.publishEvent(new UserRegisteredEvent(
            userId,
            cmd.phoneNumber(),
            cmd.email(),
            cmd.fullName(),
            cmd.birthDate(),
            cmd.gender()
        ));

        Map<String,Object> claims = Map.of(
            "userId", credential.getUserId(),
            "phoneNumber", credential.getPhoneNumber()
        );

        return new AuthResponse(
            jwtService.generateToken(credential.getUserId(), claims),
            refreshTokenService.issue(credential.getUserId()),
            credential.getUserId(),
            credential.getPhoneNumber(),
            credential.getEmail()
        );
    }
}

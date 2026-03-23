package io.zalord.auth.application;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.zalord.auth.api.AuthResponse;
import io.zalord.auth.api.LoginRequest;
import io.zalord.auth.api.RegisterRequest;
import io.zalord.auth.domain.User;
import io.zalord.auth.infrastructure.UserRepository;
import io.zalord.common.exception.InvalidCredentialsException;
import io.zalord.common.exception.UserAlreadyExistsException;
import io.zalord.common.security.JwtService;

@Service
public class AuthService {
    private final UserRepository userRepository;

    private final JwtService jwtService;

    private final PasswordEncoder passwordEncoder;

    public AuthService (UserRepository _userRepository, JwtService _jwtService, PasswordEncoder _passwordEncoder) {
        this.userRepository = _userRepository;
        this.jwtService = _jwtService;
        this.passwordEncoder = _passwordEncoder;
    }

    public AuthResponse login (LoginRequest loginRequest) {
        Optional<User> storedUser = userRepository.findByPhoneNumber(loginRequest.getPhoneNumber());
        if (storedUser.isEmpty()) {
            throw new InvalidCredentialsException("Invalid credentials.");
        }
        User user = storedUser.get();
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash()))
            throw new InvalidCredentialsException("Invalid credentials.");
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getFullName(), user.getPhoneNumber());
    }

    public AuthResponse register (RegisterRequest registerRequest) {
        if (userRepository.existsByPhoneNumber(registerRequest.getPhoneNumber()))
            throw new UserAlreadyExistsException("Phone number is already registered.");

        User newUser = new User();
        newUser.setPhoneNumber(registerRequest.getPhoneNumber());
        newUser.setFullName(registerRequest.getFullName());
        newUser.setPasswordHash(passwordEncoder.encode(registerRequest.getPassword()));

        if (registerRequest.getEmail() != null)
            newUser.setEmail(registerRequest.getEmail());

        if (registerRequest.getBirthDate() != null)
            newUser.setBirthDate(registerRequest.getBirthDate());

        if (registerRequest.getGender() != null)
            newUser.setGender(registerRequest.getGender());
        
        User user = userRepository.saveAndFlush(newUser);
        return new AuthResponse(jwtService.generateToken(user),user.getId(), user.getFullName(), user.getPhoneNumber());
    }
}

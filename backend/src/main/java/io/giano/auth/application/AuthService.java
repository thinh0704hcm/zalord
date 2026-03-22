package io.giano.auth.application;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.giano.auth.domain.User;
import io.giano.auth.infrastructure.UserRepository;
import io.giano.common.exception.InvalidCredentialsException;
import io.giano.common.security.JwtService;

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

    public AuthResponse login (String phoneNumber, String password) {
        Optional<User> storedUser = userRepository.findByPhoneNumber(phoneNumber);
        if (storedUser.isEmpty()) {
            throw new InvalidCredentialsException("Invalid credentials.");
        }
        User user = storedUser.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash()))
            throw new InvalidCredentialsException("Invalid credentials.");
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getFullName(), user.getPhoneNumber());
    }
}

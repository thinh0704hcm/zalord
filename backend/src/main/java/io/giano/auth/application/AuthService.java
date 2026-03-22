package io.giano.auth.application;

import java.util.Optional;

import javax.management.RuntimeErrorException;

import io.giano.auth.domain.User;
import io.giano.auth.infrastructure.UserRepository;

public class AuthService {
    private UserRepository userRepository;
    public AuthService (UserRepository _userRepository) {
        this.userRepository = _userRepository;
    }

    public AuthResponse login (String phoneNumber, String password) {
        Optional<User> user = userRepository.findByPhoneNumber(phoneNumber);
        if (user.isEmpty()) {
            throw new Exception();
        }
    }
}

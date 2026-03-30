package io.zalord.auth.application;

import io.zalord.auth.infrastructure.UserRepository;

public class UserService {
    private UserRepository userRepository;
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
}

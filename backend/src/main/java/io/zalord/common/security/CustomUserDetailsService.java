package io.zalord.common.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import io.zalord.user.User;
import io.zalord.user.UserRepository;

public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByPhoneNumber(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found :" + username));

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getPhoneNumber())
            .password(user.getPasswordHash())
            .build();
    }
}

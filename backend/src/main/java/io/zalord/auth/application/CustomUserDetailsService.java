package io.zalord.auth.application;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import io.zalord.auth.model.Credential;
import io.zalord.auth.repository.CredentialRepository;

public class CustomUserDetailsService implements UserDetailsService {
    private final CredentialRepository credentialRepository;

    public CustomUserDetailsService(CredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Credential credential = credentialRepository.findByPhoneNumber(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found :" + username));

        return org.springframework.security.core.userdetails.User
            .withUsername(credential.getPhoneNumber())
            .password(credential.getPasswordHash())
            .build();
    }
}

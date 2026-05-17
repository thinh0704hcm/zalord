package zalord.auth_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import zalord.auth_service.exception.UserNotFoundException;
import zalord.auth_service.model.CustomUserDetails;
import zalord.auth_service.model.User;
import zalord.auth_service.repository.RoleRepository;
import zalord.auth_service.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserDetailsServiceImpl(UserRepository userRepository, RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String phoneNumber) {
        log.debug("Loading user by phone number: {}", phoneNumber);

        Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);

        if (userOpt.isEmpty()) {
            throw new UserNotFoundException("User with phone number " + phoneNumber + " not found");
        }

        return new CustomUserDetails(userOpt.get(), roleRepository);
    }

    public CustomUserDetails loadUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        return new CustomUserDetails(user, roleRepository);
    }
}

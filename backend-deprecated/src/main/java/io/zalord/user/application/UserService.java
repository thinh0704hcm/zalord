package io.zalord.user.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import io.zalord.common.exception.MemberNotFound;
import io.zalord.user.domain.entities.User;
import io.zalord.user.repository.UserRepository;
import io.zalord.user.dto.UserResponse;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MemberNotFound("User not found"));
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getFullName(),
                user.getBirthDate(),
                user.getGender(),
                user.getCreatedAt());
    }
}

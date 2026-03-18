package io.giano.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.giano.auth.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByEmail(String email);
    Boolean existsByPhoneNumber(String phoneNumber);
    Boolean existsByEmail(String email);
}

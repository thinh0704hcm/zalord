package io.zalord.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.zalord.auth.domain.entities.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhoneNumberAndDeletedAtIsNull(String phoneNumber);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    boolean existsByPhoneNumberAndDeletedAtIsNull(String phoneNumber);
    boolean existsByEmailAndDeletedAtIsNull(String email);
}

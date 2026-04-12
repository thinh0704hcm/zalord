package io.zalord.auth.model;

import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@SQLRestriction("is_active = true")
@SQLDelete(sql = "UPDATE auth.credentials SET is_active = false WHERE id = ?")
@Table(name="credentials", schema="auth")
public class Credential {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "phone_number", nullable = false, unique = true, updatable = false)
    private String phoneNumber;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "last_login")
    private Instant lastLogin;
}

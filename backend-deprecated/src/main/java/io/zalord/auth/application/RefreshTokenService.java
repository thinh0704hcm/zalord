package io.zalord.auth.application;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import io.zalord.auth.dto.response.AuthResponse;
import io.zalord.common.exception.UnauthorizedException;
import io.zalord.common.security.JwtService;

@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    private final JwtService jwtService;
    private final StringRedisTemplate redis;

    @Value("${jwt.refresh_expiry_days}")
    private int refreshExpiryDays;

    public RefreshTokenService(JwtService jwtService, StringRedisTemplate redis) {
        this.jwtService = jwtService;
        this.redis = redis;
    }

    public String issue(UUID userId) {
        UUID jti = UUID.randomUUID();
        String token = jwtService.generateRefreshToken(userId, jti);
        redis.opsForValue().set(KEY_PREFIX + jti, userId.toString(), Duration.ofDays(refreshExpiryDays));
        return token;
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtService.isValidToken(refreshToken))
            throw new UnauthorizedException("Invalid refresh token");

        String jti = jwtService.extractJti(refreshToken);
        String storedUserId = redis.opsForValue().get(KEY_PREFIX + jti);

        if (storedUserId == null)
            throw new UnauthorizedException("Refresh token has been revoked");

        UUID userId = UUID.fromString(storedUserId);
        String phoneNumber = jwtService.extractClaim(refreshToken, "phoneNumber");

        Map<String, Object> claims = Map.of(
                "userId", userId,
                "phoneNumber", phoneNumber != null ? phoneNumber : "");

        String newAccessToken = jwtService.generateToken(userId, claims);
        String newRefreshToken = issue(userId);

        // Rotate: revoke old token
        redis.delete(KEY_PREFIX + jti);

        return new AuthResponse(newAccessToken, newRefreshToken, userId, phoneNumber, null);
    }

    public void revoke(String refreshToken) {
        if (!jwtService.isValidToken(refreshToken))
            throw new UnauthorizedException("Invalid refresh token");

        String jti = jwtService.extractJti(refreshToken);
        redis.delete(KEY_PREFIX + jti);
    }
}

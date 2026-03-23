package io.zalord.common.security;

import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.zalord.auth.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiry_minutes}")
    private int expiryMinutes;

    public String generateToken(User user) {
        return createToken(user);
    }

    private String createToken(User user) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder()
               .subject(user.getId().toString())
               .issuedAt(Date.from(now))
               .expiration(Date.from(now.plus(expiryMinutes, ChronoUnit.MINUTES)))
               .signWith(getSignKey())
               .compact();
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

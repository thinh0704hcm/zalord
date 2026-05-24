package zalord.auth_service.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import zalord.auth_service.model.CustomUserDetails;

import javax.crypto.SecretKey;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${spring.jwt.secret}")
    private String secretKey;

    // Access-token lifetime in MINUTES (converted to ms when building the token).
    @Value("${spring.jwt.expiration}")
    private Long expirationMinutes;

    // Must equal the Kong consumer credential `key` so the gateway can verify.
    @Value("${spring.jwt.issuer}")
    private String issuer;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(CustomUserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return generateAccessToken(userDetails.getUserId(), roles);
    }

    // Used by login (roles from CustomUserDetails) and refresh (roles from DB).
    public String generateAccessToken(UUID userId, Collection<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        return createToken(claims, userId.toString());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        long nowMs = System.currentTimeMillis();
        long expiryMs = nowMs + (expirationMinutes * 60_000L);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(issuer)                          // Kong matches the consumer on this
                .setIssuedAt(new Date(nowMs))
                .setExpiration(new Date(expiryMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Boolean validateToken(String token, CustomUserDetails userDetails) {
        final UUID id = UUID.fromString(extractUserId(token));
        return (id.equals(userDetails.getUserId()) && !isTokenExpired(token));
    }

    public Boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}

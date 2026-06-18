package zalord.auth_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zalord.auth_service.exception.InvalidCredentialsException;
import zalord.auth_service.model.Session;
import zalord.auth_service.model.User;
import zalord.auth_service.repository.SessionRepository;
import zalord.auth_service.service.ISessionService;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@Slf4j
public class SessionServiceImpl implements ISessionService {

    private final SessionRepository sessionRepository;

    // Refresh-token lifetime in DAYS.
    @Value("${spring.refresh.expiration}")
    private long refreshExpirationDays;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    public SessionServiceImpl(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    @Transactional
    public String createSession(User user) {
        String token = generateOpaqueToken();

        Session session = new Session();
        session.setUser(user);
        session.setToken(token);
        session.setExpiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS));
        sessionRepository.save(session);

        log.debug("Created refresh session for userId={}", user.getId());
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public Session validateRefreshToken(String refreshToken) {
        Session session = sessionRepository.findByTokenAndDeletedAtIsNull(refreshToken)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        return session;
    }

    @Override
    @Transactional
    public void revoke(String refreshToken) {
        sessionRepository.findByTokenAndDeletedAtIsNull(refreshToken).ifPresent(session -> {
            session.setDeletedAt(Instant.now());
            sessionRepository.save(session);
            log.debug("Revoked refresh session id={}", session.getId());
        });
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return urlEncoder.encodeToString(bytes);
    }
}

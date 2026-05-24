package zalord.auth_service.service;

import zalord.auth_service.model.Session;
import zalord.auth_service.model.User;

public interface ISessionService {

    /** Create a new refresh session for the user and return the opaque refresh token. */
    String createSession(User user);

    /** Resolve a non-revoked, non-expired session by its refresh token, or throw. */
    Session validateRefreshToken(String refreshToken);

    /** Soft-delete (revoke) the session that owns this refresh token. Idempotent. */
    void revoke(String refreshToken);
}

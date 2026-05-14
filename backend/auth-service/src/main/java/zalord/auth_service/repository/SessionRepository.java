package zalord.auth_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zalord.auth_service.model.Session;

import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
}

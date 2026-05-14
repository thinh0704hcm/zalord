package zalord.auth_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zalord.auth_service.model.User;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
}

package zalord.auth_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zalord.auth_service.model.UserRoles;

import java.util.UUID;

public interface UserRolesRepository extends JpaRepository<UserRoles, UUID> {
}

package zalord.auth_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.auth_service.model.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Query(value = """
        SELECT r.name
        FROM roles r
        JOIN user_roles ur ON r.id = ur.role_id
        WHERE ur.user_id = :userId
    """,nativeQuery = true)
    List<String> findRolesByUserId(@Param("userId") UUID userId);

    Optional<Role> findByName(String name);
}

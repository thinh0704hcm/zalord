package zalord.group_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zalord.group_service.model.Group;

import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {
}

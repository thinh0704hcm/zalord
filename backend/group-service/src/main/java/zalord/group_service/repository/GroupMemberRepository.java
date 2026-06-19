package zalord.group_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.group_service.model.GroupMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    List<GroupMember> findAllByGroupId(UUID groupId);

    @Query("SELECT gm.groupId FROM GroupMember gm WHERE gm.userId = :userId")
    Page<UUID> findGroupIdsByUserId(@Param("userId") UUID userId, Pageable pageable);

    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);
}

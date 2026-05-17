package zalord.auth_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zalord.auth_service.model.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
          SELECT * FROM outbox_events                                                                                                                                              
          WHERE published_at IS NULL                                                                                                                                             
          ORDER BY created_at                                                                                                                                                      
          LIMIT :batchSize
          FOR UPDATE SKIP LOCKED                                                                                                                                                   
          """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("batchSize") int batchSize);
}

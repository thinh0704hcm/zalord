package zalord.message_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zalord.message_service.model.DirectLookup;

public interface DirectLookupRepository extends JpaRepository<DirectLookup, String> {
}

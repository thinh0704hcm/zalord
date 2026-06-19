package zalord.message_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zalord.message_service.model.Conversation;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
}

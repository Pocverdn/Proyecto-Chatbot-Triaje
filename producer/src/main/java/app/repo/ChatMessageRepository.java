package app.repo;

import app.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findTop100ByUserIdOrderByCreatedAtDesc(String userId);
}

package app.repo;

import app.model.ChatMessage;
import app.repo.ChatMessageRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Listens to the instance-specific replication queue bound to the fanout exchange.
 */
@Component
public class ReplicationListener {

    private final ChatMessageRepository repo;

    public ReplicationListener(ChatMessageRepository repo) {
        this.repo = repo;
    }

    @RabbitListener(queues = "#{replicationQueue.name}") //
    public void onReplicate(Map<String, Object> payload) {
        try {
            String id = (String) payload.get("id");
            String userId = (String) payload.get("userId");
            String role = (String) payload.get("role");
            String content = (String) payload.get("content");
            String createdAtStr = (String) payload.get("createdAt");

            if (id == null || createdAtStr == null) return;

            Instant createdAt = Instant.parse(createdAtStr);
            Optional<ChatMessage> existing = repo.findById(id);

            if (existing.isEmpty()) {
                ChatMessage msg = new ChatMessage(id, userId, role, content, createdAt);
                repo.save(msg);
            } else {
                ChatMessage ex = existing.get();
                if (ex.getCreatedAt() == null || createdAt.isAfter(ex.getCreatedAt())) {
                    ex.setContent(content);
                    ex.setRole(role);
                    ex.setCreatedAt(createdAt);
                    ex.setUserId(userId);
                    repo.save(ex);
                }
            }

        } catch (Exception e) {
            System.err.println("[ReplicationListener] Error processing replication: " + e.getMessage());
        }
    }
}

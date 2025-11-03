package app.repo;

import app.model.ChatMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import app.repo.ChatMessageRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Listens to replication messages broadcast via the fanout exchange.
 *
 * Each instance of the application has its own auto-generated replication queue,
 * bound to the same fanout exchange. When one instance publishes a message to the
 * exchange, *all* instances receive it here to keep their local databases consistent.
 */
@Component
public class ReplicationListener {

    private final ChatMessageRepository repo;

    public ReplicationListener(ChatMessageRepository repo) {
        this.repo = repo;
    }

    /**
     * Handles replicated messages broadcast through the fanout exchange.
     * The queue name is dynamically resolved from the replicationQueue bean.
     */
    @RabbitListener(queues = "#{replicationQueue.name}")
    public void onReplicate(Map<String, Object> payload) {
        try {
            // Extract message fields
            String id = (String) payload.get("id");
            String userId = (String) payload.get("userId");
            String role = (String) payload.get("role");
            String content = (String) payload.get("content");
            String createdAtStr = (String) payload.get("createdAt");

            if (id == null || userId == null || content == null || createdAtStr == null) {
                System.err.println("⚠️ Invalid replication payload: " + payload);
                return;
            }

            Instant createdAt = Instant.parse(createdAtStr);

            // Check for existing message in local DB
            Optional<ChatMessage> existing = repo.findById(id);

            if (existing.isEmpty()) {
                // Insert new message if not found
                ChatMessage msg = new ChatMessage(id, userId, role, content, createdAt);
                repo.save(msg);
                System.out.println("🟢 Replicated new message from user " + userId);
            } else {
                // Update if newer
                ChatMessage ex = existing.get();
                if (ex.getCreatedAt() == null || createdAt.isAfter(ex.getCreatedAt())) {
                    ex.setContent(content);
                    ex.setRole(role);
                    ex.setCreatedAt(createdAt);
                    ex.setUserId(userId);
                    repo.save(ex);
                    System.out.println("🟡 Updated existing replicated message for user " + userId);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("❌ Error in ReplicationListener: " + ex.getMessage());
        }
    }
}

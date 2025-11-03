package app.controller;

import app.config.RabbitConfig;
import app.model.ChatMessage;
import app.repo.ChatMessageRepository;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller responsible for handling chat interactions.
 *
 * Responsibilities:
 *  - Accepts user messages via POST requests.
 *  - Persists messages to the database.
 *  - Sends messages to RabbitMQ for AI processing.
 *  - Sends replication messages for distributed persistence.
 *  - Returns chat history for a specific user.
 */
@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "${frontend.url}") // Allow CORS from configured frontend
public class ChatController {

    private final ChatMessageRepository repo;
    private final RabbitTemplate rabbitTemplate;
    private final Queue streamQueue; // Producer's stream queue

    public ChatController(ChatMessageRepository repo, RabbitTemplate rabbitTemplate, Queue streamQueue) {
        this.repo = repo;
        this.rabbitTemplate = rabbitTemplate;
        this.streamQueue = streamQueue;
    }

    /**
     * Endpoint: POST /chat
     *
     * Receives a chat message from the frontend.
     *  1. Validates input (must have userId and message).
     *  2. Saves the message to the database.
     *  3. Publishes the message to the processing queue for AI workers.
     *  4. Sends replication message for database consistency.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> postMessage(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String content = body.get("message");

        if (userId == null || content == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId and message required"));
        }

        // 1️⃣ Save user message in database
        String id = UUID.randomUUID().toString();
        ChatMessage msg = new ChatMessage(id, userId, "user", content, Instant.now());
        repo.save(msg);

        // 2️⃣ Send message to worker for AI processing
        Map<String, Object> payload = Map.of(
                "id", msg.getId(),
                "userId", msg.getUserId(),
                "role", msg.getRole(),
                "content", msg.getContent(),
                "createdAt", msg.getCreatedAt().toString(),
                "streamQueueName", streamQueue.getName() // pass this producer's stream queue
        );
        rabbitTemplate.convertAndSend(RabbitConfig.PROCESS_QUEUE, payload);

        // 3️⃣ Send replication message (database replication only)
        rabbitTemplate.convertAndSend(RabbitConfig.REPL_EXCHANGE, "", Map.of(
                "id", msg.getId(),
                "userId", msg.getUserId(),
                "role", msg.getRole(),
                "content", msg.getContent(),
                "createdAt", msg.getCreatedAt().toString()
        ));

        return ResponseEntity.ok(Map.of("status", "queued", "id", id));
    }

    /**
     * Endpoint: GET /chat/{userId}
     *
     * Retrieves the last 100 messages for the given user.
     */
    @GetMapping("/{userId}")
    public List<Map<String, String>> getHistory(@PathVariable String userId) {
        var list = repo.findTop100ByUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, String>> out = new ArrayList<>();
        for (var m : list) {
            out.add(Map.of(
                    "id", m.getId(),
                    "userId", m.getUserId(),
                    "role", m.getRole(),
                    "content", m.getContent(),
                    "createdAt", m.getCreatedAt().toString()
            ));
        }
        return out;
    }
}

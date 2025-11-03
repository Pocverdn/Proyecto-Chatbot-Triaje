// src/main/java/app/ChatWorker.java
package app;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * ChatWorker
 *
 * This Spring Boot service listens to the RabbitMQ queue ("chat-queue")
 * for incoming user messages, sends them to OpenAI's Chat Completions API,
 * and logs the model's reply.
 *
 * It acts as the "worker" in a distributed architecture:
 * - The frontend (Next.js) sends user messages to the backend.
 * - The backend publishes messages to RabbitMQ.
 * - This worker consumes from RabbitMQ, calls OpenAI, and processes responses.
 */
@Service
public class ChatWorker {

    private final WebClient webClient;

    /**
     * Initializes a reactive WebClient for making HTTPS requests to the OpenAI API.
     * The API key is securely loaded from the environment variable OPENAI_API_KEY.
     */
    public ChatWorker() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .build();
    }

    /**
     * RabbitMQ listener that consumes messages from the "chat-queue".
     * The payload is expected to contain:
     * - userId: unique identifier for the chat session
     * - message: user text input
     *
     * Upon receiving a message, it calls OpenAI's Chat API
     * and retrieves the assistant's response.
     */
    @RabbitListener(queues = "chat-queue")
    public void processMessage(Map<String, String> payload) {
        String userId = payload.get("userId");
        String userMessage = payload.get("message");

        System.out.println("Processing new message from user " + userId + ": " + userMessage);

        try {
            // Call OpenAI’s chat completion endpoint
            String aiResponse = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(Map.of(
                            "model", "gpt-4o-mini",
                            "messages", new Object[]{
                                    Map.of("role", "user", "content", userMessage)
                            }
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(res -> {
                        // Parse JSON: extract the assistant's reply
                        var choices = (java.util.List<Map<String, Object>>) res.get("choices");
                        if (choices == null || choices.isEmpty()) {
                            return "(No response from model)";
                        }
                        var msg = (Map<String, Object>) choices.get(0).get("message");
                        return (String) msg.get("content");
                    })
                    .block(); // Block since we’re in a background worker

            // Log the model's output for visibility
            System.out.println("AI reply for " + userId + ": " + aiResponse);

            // Optionally: publish the response to replication queue here
            // (if you want other nodes to receive it)
            // rabbitTemplate.convertAndSend("chat.replication.queue", Map.of(...));

        } catch (Exception e) {
            System.err.println("Error processing message for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}

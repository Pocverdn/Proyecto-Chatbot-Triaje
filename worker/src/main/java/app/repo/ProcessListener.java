package app.repo;

import app.config.RabbitConfig;
import app.model.ChatMessage;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.ChatModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class ProcessListener {

    private final ChatMessageRepository repo;
    private final RabbitTemplate rabbitTemplate;
    private final OpenAIClient client;

    public ProcessListener(ChatMessageRepository repo, RabbitTemplate rabbitTemplate) {
        this.repo = repo;
        this.rabbitTemplate = rabbitTemplate;
        this.client = OpenAIOkHttpClient.fromEnv();
    }

    @RabbitListener(queues = RabbitConfig.PROCESS_QUEUE)
    public void onProcess(Map<String, Object> payload) {
        try {
            String userId = (String) payload.get("userId");
            String content = (String) payload.get("content");
            String streamQueueName = (String) payload.get("streamQueueName"); 

            if (userId == null || content == null) {
                System.err.println("⚠️ Invalid payload: " + payload);
                return;
            }

            // Build conversation context
            List<ChatMessage> history = repo.findTop100ByUserIdOrderByCreatedAtDesc(userId);
            Collections.reverse(history);

            StringBuilder conversation = new StringBuilder();
            for (ChatMessage msg : history) {
                conversation.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            conversation.append("user: ").append(content);

            // OpenAI streaming parameters
            var params = ChatCompletionCreateParams.builder()
                    .addUserMessage(conversation.toString())
                    .model(ChatModel.GPT_4_1)
                    .build();

            StringBuilder fullReply = new StringBuilder();

            client.async().chat().completions().createStreaming(params)
                    .subscribe(new AsyncStreamResponse.Handler<ChatCompletionChunk>() {
                        @Override
                        public void onNext(ChatCompletionChunk chunk) {
                            String token = chunk.choices().get(0).delta().content().orElse("");
                            if (!token.isEmpty()) {
                                System.out.print(token);
                                fullReply.append(token);

                                // 🔹 Send token to producer via user-specific queue
                                rabbitTemplate.convertAndSend(
                                // Send directly to the producer's ephemeral stream queue
                                streamQueueName,  // the producer queue name passed in the payload
                                Map.of(
                                    "userId", userId,
                                    "event", "token",
                                    "content", token
                                )
                            );
                            }
                        }

                        @Override
                        public void onComplete(Optional<Throwable> error) {
                            if (error.isPresent()) {
                                System.err.println("\n❌ Stream error!");
                                error.get().printStackTrace();
                            } else {
                                System.out.println("\n✅ Stream completed!");

                                // Save final assistant message to DB
                                ChatMessage assistantMsg = new ChatMessage();
                                assistantMsg.setUserId(userId);
                                assistantMsg.setRole("assistant");
                                assistantMsg.setContent(fullReply.toString());
                                assistantMsg.setCreatedAt(Instant.now());
                                repo.save(assistantMsg);
                                // Also replicate to database exchange
                                rabbitTemplate.convertAndSend(
                                        RabbitConfig.REPL_EXCHANGE,
                                        "",
                                        Map.of(
                                                "id", assistantMsg.getId(),
                                                "userId", assistantMsg.getUserId(),
                                                "role", assistantMsg.getRole(),
                                                "content", assistantMsg.getContent(),
                                                "createdAt", assistantMsg.getCreatedAt().toString()
                                        )
                                );
                                // Notify producer that stream is complete
                                rabbitTemplate.convertAndSend(streamQueueName, 
                                Map.of(
                                        "userId", userId,
                                        "event", "complete",
                                        "content", "t"
                                ));

                                
                                
                            }
                        }
                    })
                    .onCompleteFuture()
                    .whenComplete((unused, error) -> {
                        if (error != null) {
                            System.err.println("⚠️ Stream future completed with error!");
                            error.printStackTrace();
                        } else {
                            System.out.println("🔚 Stream fully completed!"+streamQueueName);
                        }
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

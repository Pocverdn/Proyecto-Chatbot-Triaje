package app.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StreamService {

    // Hold active emitters by user ID
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Creates and registers a new SSE emitter for the given user.
     */
    public SseEmitter connect(String userId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> {
            System.out.println("🧹 SSE completed for user: " + userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            System.out.println("⏱️ SSE timeout for user: " + userId);
            emitters.remove(userId);
        });

        emitter.onError((e) -> {
            System.out.println("❌ SSE error for user: " + userId + " | " + e.getMessage());
            emitters.remove(userId);
        });

        System.out.println("🔗 SSE connected for user: " + userId);
        return emitter;
    }

    /**
     * Sends a streaming token to the user's emitter.
     */
    public void sendToken(String userId, String token) {
    SseEmitter emitter = emitters.get(userId);
    if (emitter != null) {
        try {
            // Encode space-only tokens safely
            String safeToken = token.replace(" ", "\u00A0"); // non-breaking space
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(safeToken));
        } catch (IOException e) {
            emitter.completeWithError(e);
            emitters.remove(userId);
        }
    }
}


    /**
     * Completes the stream and removes the emitter.
     */
    public void complete(String userId) {
        SseEmitter emitter = emitters.get(userId);
        // if (emitter != null) {
            try {
                System.out.println("✅ Completing SSE stream for user: " + userId);
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("done"));
                emitter.complete();
                emitters.remove(userId);
            } catch (IOException e) {
                System.out.println("⚠️ Error completing stream for " + userId + ": " + e.getMessage());
            }
        // } else {
        //     System.out.println("🚫 Tried to complete, but no emitter found for " + userId);
        // }
    }
}

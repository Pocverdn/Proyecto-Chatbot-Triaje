package app.repo;

import app.service.StreamService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * Listens to AI worker streams for specific users and pushes tokens to SSE.
 */
@Component
public class ProducerStreamListener {

    private final StreamService streamService;

    @Autowired
    public ProducerStreamListener(StreamService streamService) {
        this.streamService = streamService;
    }

    @RabbitListener(queues = "#{streamQueue.name}")
    public void onStreamMessage(Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String event = (String) payload.get("event");
        String content = (String) payload.get("content");

        //if (userId == null || event == null) return;
        switch (event) {
            case "token" -> {
                //if (content != null) 
                streamService.sendToken(userId, content);
            }
            case "complete" -> streamService.complete(userId);
        }
    }
}


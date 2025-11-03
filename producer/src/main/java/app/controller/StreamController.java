package app.controller;

import app.service.StreamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "${frontend.url}")
public class StreamController {

    @Autowired
    private StreamService streamService;

    /**
     * Endpoint for client to connect to SSE stream for a specific user.
     */
    @GetMapping(value = "/stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String userId) {
        return streamService.connect(userId);
    }
}

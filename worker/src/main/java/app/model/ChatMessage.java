package app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    private String id; // UUID

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String role; // "user" or "assistant"

    @Column(columnDefinition = "CLOB")
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    public ChatMessage() {}

    public ChatMessage(String id, String userId, String role, String content, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    // getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

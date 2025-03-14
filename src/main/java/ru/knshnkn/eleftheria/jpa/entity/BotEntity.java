package ru.knshnkn.eleftheria.jpa.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bots")
public class BotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_chat_id")
    private String creatorChatId;
    private String token;
    private String admin_chat_id;
    private String status;
    private String start_message;
    private String randomName;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime last_activity_at;

    @PrePersist
    protected void onCreate() {
        created_at = LocalDateTime.now();
        updated_at = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updated_at = LocalDateTime.now();
    }
}

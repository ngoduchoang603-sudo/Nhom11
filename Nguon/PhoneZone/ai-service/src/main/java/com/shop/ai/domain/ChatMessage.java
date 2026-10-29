package com.shop.ai.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Domain entity. Conversation audit data is owned by the AI bounded context. */
@Entity
@Table(name = "chat_messages")
public class ChatMessage extends PanacheEntity {
    @Column(nullable = false, length = 100) public String sessionId;
    @Column(nullable = false, length = 20) public String role;
    @Column(nullable = false, length = 4000) public String content;
    @Column(nullable = false) public Instant createdAt;
}

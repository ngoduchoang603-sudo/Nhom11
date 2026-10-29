package com.shop.ai.adapter.out.persistence;

import com.shop.ai.application.port.out.ConversationStore;
import com.shop.ai.domain.ChatMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

/** PostgreSQL adapter; application code does not know Panache/JPA. */
@ApplicationScoped
public class PostgresConversationStore implements ConversationStore {
    @Override @Transactional
    public void save(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.sessionId = sessionId; message.role = role; message.content = content; message.createdAt = Instant.now();
        message.persist();
    }

    @Override @Transactional
    public List<Turn> recent(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 12));
        List<ChatMessage> newestFirst = ChatMessage.find("sessionId = ?1 order by createdAt desc", sessionId)
                .page(0, safeLimit).list();
        return newestFirst.reversed().stream().map(item -> new Turn(item.role, item.content)).toList();
    }
}

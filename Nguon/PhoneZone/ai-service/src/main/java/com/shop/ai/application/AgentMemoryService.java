package com.shop.ai.application;

import com.shop.ai.domain.AgentMemory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class AgentMemoryService {
    private static final Duration TTL = Duration.ofMinutes(30);
    @Transactional public void save(String sessionId, String productId, String productName, String catalogJson, String contextJson) {
        if (sessionId == null || sessionId.isBlank()) return;
        AgentMemory memory = AgentMemory.find("sessionId", sessionId).firstResult();
        if (memory == null) {
            memory = new AgentMemory();
            memory.sessionId = sessionId;
            memory.productId = productId;
            memory.productName = productName;
            memory.catalogJson = catalogJson;
            memory.contextJson = contextJson;
            memory.expiresAt = Instant.now().plus(TTL);
            memory.persist();
            return;
        }
        memory.productId = productId; memory.productName = productName;
        if (catalogJson != null) memory.catalogJson = catalogJson;
        if (contextJson != null) memory.contextJson = contextJson;
        memory.expiresAt = Instant.now().plus(TTL);
    }
    @Transactional public Memory get(String sessionId) {
        AgentMemory memory = AgentMemory.find("sessionId", sessionId).firstResult();
        if (memory == null) return null;
        if (memory.expiresAt.isBefore(Instant.now())) { memory.delete(); return null; }
        return new Memory(memory.productId, memory.productName, memory.catalogJson, memory.contextJson);
    }
    public record Memory(String productId, String productName, String catalogJson, String contextJson) { }
}

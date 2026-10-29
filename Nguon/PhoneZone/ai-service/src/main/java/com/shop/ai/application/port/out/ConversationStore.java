package com.shop.ai.application.port.out;

import java.util.List;

public interface ConversationStore {
    void save(String sessionId, String role, String content);
    List<Turn> recent(String sessionId, int limit);
    record Turn(String role, String content) { }
}

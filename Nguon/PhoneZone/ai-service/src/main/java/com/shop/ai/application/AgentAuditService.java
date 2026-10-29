package com.shop.ai.application;

import com.shop.ai.domain.AgentRunAudit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AgentAuditService {
    @Transactional public void record(String sessionId, List<String> tools, long latencyMs, String route, int promptChars, int answerChars) {
        AgentRunAudit item = new AgentRunAudit(); item.sessionId = sessionId; item.tools = tools;
        item.latencyMs = latencyMs; item.route = route; item.createdAt = Instant.now();
        item.tokenUsage = Map.of("provider", "not-reported", "promptChars", promptChars, "answerChars", answerChars);
        item.persist();
    }
}

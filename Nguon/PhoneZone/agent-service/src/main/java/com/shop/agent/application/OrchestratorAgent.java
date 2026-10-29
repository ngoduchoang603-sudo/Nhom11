package com.shop.agent.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Manager/Orchestrator boundary for the multi-agent design.
 * It owns routing and the allow-list; specialised capabilities remain inside
 * ShoppingAgent until they are independently deployed.  No write tool can be
 * reached without a customer confirmation proposal.
 */
@ApplicationScoped
public class OrchestratorAgent {
    @Inject ShoppingAgent shoppingAgent;

    public ShoppingAgent.AgentResult plan(String sessionId, Long userId, String question) {
        return shoppingAgent.prepare(sessionId, userId, question);
    }
}

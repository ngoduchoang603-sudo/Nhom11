package com.shop.ai.application.port.out;

import java.util.List;

/** Port to the separately deployed read-only agent orchestrator. */
public interface AgentContextProvider {
    AgentContext prepare(String sessionId, Long userId, String question);
    record CartProposal(String productId, String productName, long quantity, boolean checkoutAfterAdd) {
        public CartProposal(String productId, String productName, long quantity) { this(productId, productName, quantity, false); }
    }
    record ActionProposal(String type, String title, String message, String targetUrl) { }
    record ToolCall(String name, boolean readOnly, String dataSource) { }
    record AgentContext(String context, List<String> toolsUsed, CartProposal proposedCart, ActionProposal proposedAction, List<ToolCall> toolCalls) {
        public AgentContext(String context, List<String> toolsUsed, CartProposal proposedCart) { this(context, toolsUsed, proposedCart, null, List.of()); }
        public AgentContext(String context, List<String> toolsUsed, CartProposal proposedCart, ActionProposal proposedAction) { this(context, toolsUsed, proposedCart, proposedAction, List.of()); }
    }
}

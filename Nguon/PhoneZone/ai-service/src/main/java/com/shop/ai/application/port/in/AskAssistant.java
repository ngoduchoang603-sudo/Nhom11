package com.shop.ai.application.port.in;

public interface AskAssistant {
    Answer ask(String sessionId, Long userId, String question);
    record CartProposal(String productId, String productName, long quantity, boolean checkoutAfterAdd) {
        public CartProposal(String productId, String productName, long quantity) { this(productId, productName, quantity, false); }
    }
    record ActionProposal(String type, String title, String message, String targetUrl) { }
    record ToolCall(String name, boolean readOnly, String dataSource) { }
    record Answer(String answer, String source, java.util.List<String> toolsUsed, CartProposal proposedCart, ActionProposal proposedAction, java.util.List<ToolCall> toolCalls) { }
}

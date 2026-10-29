package com.shop.ai.application;

import com.shop.ai.application.port.in.AskAssistant;
import com.shop.ai.application.port.out.ConversationStore;
import com.shop.ai.application.port.out.AssistantModel;
import com.shop.ai.application.port.out.AgentContextProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/** Application use case. Tool selection is intentionally constrained to safe, read-only capabilities. */
@ApplicationScoped
public class AssistantService implements AskAssistant {
    @Inject ConversationStore conversationStore;
    @Inject AssistantModel assistantModel;
    @Inject AgentContextProvider agentContextProvider;
    @Inject AgentAuditService agentAudit;

    @Override
    public Answer ask(String sessionId, Long userId, String question) {
        long started = System.nanoTime();
        conversationStore.save(sessionId, "USER", question);
        AgentContextProvider.AgentContext agentContext = agentContextProvider.prepare(sessionId, userId, question);
        boolean hasVerifiedToolResult = hasVerifiedToolResult(agentContext);
        String catalogContext = hasVerifiedToolResult && !agentContext.context().isBlank() ? " " + agentContext.context() : "";
        String systemInstruction = "You are the PhoneZone mobile-phone shop assistant. Reply in Vietnamese. The shop sells only products returned by its catalog tools; never mention, recommend, or invent laptops, accessories, products, prices, specifications, stock, or brands not returned by those tools. " +
                "If the customer asks to buy, order, cancel, or requests a quantity without naming an identifiable product, ask one concise clarifying question. " +
                "Only state that a named product is unavailable after an explicit catalog lookup returned no match; never infer unavailability from a missing or failed tool result. " +
                "You may advise customers, " +
                "but must never create orders, modify data, request secrets, or claim an action was completed. " +
                "For price, stock, product or color questions, you MUST use only current tool results and never use old conversation context. " +
                "If variant data is unavailable, say so plainly. If tool results are supplied, use only those facts for price and stock statements.";
        // Gia va ton kho phai luon lay tu tool, khong de LLM tu suy dien sai.
        boolean asksForPriceRange = question.matches("(?is).*\\d+\\s*(triệu|trieu|tr).*" );
        boolean simpleCatalogQuestion = question.matches("(?is).*(giá|tồn|còn hàng|màu|mau|ram|dung lượng|bộ nhớ|storage|pin).*" );
        // Neu agent da tra cuu danh muc thi chi tra ve du lieu da duoc kiem chung.
        // Khong gui them qua LLM de tranh hallucination (vi du tu dien thoai sang laptop gaming).
        String answer = hasVerifiedToolResult || agentContext.proposedCart() != null
                ? safeFallback(agentContext)
                : assistantModel.complete(systemInstruction + catalogContext + conversationContext(sessionId), question)
                        .orElseGet(() -> safeFallback(agentContext));
        conversationStore.save(sessionId, "ASSISTANT", answer);
        agentAudit.record(sessionId, agentContext.toolsUsed(), (System.nanoTime() - started) / 1_000_000L,
                agentContext.toolsUsed().isEmpty() ? "llm-or-fallback" : "agent-tool-route", question.length(), answer.length());
        AskAssistant.CartProposal proposedCart = agentContext.proposedCart() == null ? null
                : new AskAssistant.CartProposal(agentContext.proposedCart().productId(), agentContext.proposedCart().productName(), agentContext.proposedCart().quantity(), agentContext.proposedCart().checkoutAfterAdd());
        AskAssistant.ActionProposal proposedAction = agentContext.proposedAction() == null ? null
                : new AskAssistant.ActionProposal(agentContext.proposedAction().type(), agentContext.proposedAction().title(), agentContext.proposedAction().message(), agentContext.proposedAction().targetUrl());
        List<AskAssistant.ToolCall> toolCalls = agentContext.toolCalls().stream()
                .map(call -> new AskAssistant.ToolCall(call.name(), call.readOnly(), call.dataSource())).toList();
        return new Answer(answer, agentContext.toolsUsed().isEmpty() ? "llm-or-safe-fallback" : "agent-service+llm-or-safe-fallback", agentContext.toolsUsed(), proposedCart, proposedAction, toolCalls);
    }

    /** Van tra loi huu ich khi nha cung cap LLM het quota hoac tam thoi khong san sang. */
    private String safeFallback(AgentContextProvider.AgentContext agentContext) {
        if (hasVerifiedToolResult(agentContext)) {
            return "Tôi đã tra cứu trực tiếp từ danh mục PhoneZone.\n\n" + agentContext.context()
                    + "\n\nBạn có thể cho tôi thêm nhu cầu hoặc mức giá để lọc tiếp.";
        }
        return "Mình chưa có đủ thông tin để thực hiện yêu cầu này. Bạn hãy cho biết rõ sản phẩm, số lượng hoặc thao tác cần làm; mình sẽ kiểm tra và hướng dẫn bước tiếp theo.";
    }

    /** A tool name alone is not evidence: catalog search can legitimately return no match. */
    private boolean hasVerifiedToolResult(AgentContextProvider.AgentContext agentContext) {
        if (agentContext.toolsUsed().isEmpty() || agentContext.context().isBlank()) return false;
        String context = agentContext.context().toLowerCase();
        return !context.contains("chưa tìm thấy sản phẩm") && !context.contains("tôi chưa xác định được sản phẩm");
    }

    /** The model sees a small, server-side history, so pronouns such as “máy đó”
     * remain meaningful without exposing an unlimited customer transcript. */
    private String conversationContext(String sessionId) {
        List<ConversationStore.Turn> turns = conversationStore.recent(sessionId, 12);
        if (turns.isEmpty()) return "";
        StringBuilder history = new StringBuilder("\nRecent conversation (use only to resolve references, never invent catalog facts):\n");
        for (ConversationStore.Turn turn : turns) {
            String content = turn.content() == null ? "" : turn.content().replaceAll("\\s+", " ");
            history.append(turn.role()).append(": ").append(content.length() > 500 ? content.substring(0, 500) : content).append("\n");
        }
        return history.toString();
    }
}

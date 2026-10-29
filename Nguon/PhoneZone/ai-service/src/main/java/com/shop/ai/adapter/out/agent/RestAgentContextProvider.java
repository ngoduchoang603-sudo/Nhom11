package com.shop.ai.adapter.out.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.ai.application.port.out.AgentContextProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** REST adapter: ai-service delegates tool planning/execution to agent-service. */
@ApplicationScoped
public class RestAgentContextProvider implements AgentContextProvider {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    @ConfigProperty(name = "shop.agent-service.url") String agentServiceUrl;

    @Override public AgentContext prepare(String sessionId, Long userId, String question) {
        try {
            var requestBody = mapper.createObjectNode().put("question", question).put("sessionId", sessionId);
            if (userId != null) requestBody.put("userId", userId);
            String payload = requestBody.toString();
            HttpRequest request = HttpRequest.newBuilder(URI.create(agentServiceUrl + "/plan"))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            JsonNode response = mapper.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            List<String> tools = new ArrayList<>();
            response.path("toolsUsed").forEach(tool -> tools.add(tool.asText()));
            List<AgentContextProvider.ToolCall> toolCalls = new ArrayList<>();
            response.path("toolCalls").forEach(call -> toolCalls.add(new AgentContextProvider.ToolCall(
                    call.path("name").asText(), call.path("readOnly").asBoolean(true), call.path("dataSource").asText())));
            JsonNode proposal = response.path("proposedCart");
            CartProposal cartProposal = proposal.isObject()
                    ? new CartProposal(proposal.path("productId").asText(), proposal.path("productName").asText(), proposal.path("quantity").asLong(1), proposal.path("checkoutAfterAdd").asBoolean(false))
                    : null;
            JsonNode action = response.path("proposedAction");
            ActionProposal actionProposal = action.isObject()
                    ? new ActionProposal(action.path("type").asText(), action.path("title").asText(), action.path("message").asText(), action.path("targetUrl").asText())
                    : null;
            return new AgentContext(response.path("context").asText(""), tools, cartProposal, actionProposal, toolCalls);
        } catch (Exception ignored) {
            return new AgentContext("", List.of(), null);
        }
    }
}

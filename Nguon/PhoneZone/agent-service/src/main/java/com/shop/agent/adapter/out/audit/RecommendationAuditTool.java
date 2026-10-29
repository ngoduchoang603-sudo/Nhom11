package com.shop.agent.adapter.out.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@ApplicationScoped
public class RecommendationAuditTool {
    private final HttpClient http = HttpClient.newHttpClient(); private final ObjectMapper mapper = new ObjectMapper();
    @ConfigProperty(name = "shop.ai-service.url") String aiServiceUrl;
    public void record(String sessionId, Long userId, List<String> ids) {
        try {
            var body = mapper.createObjectNode().put("sessionId", sessionId);
            if (userId != null) body.put("userId", userId);
            var products = body.putArray("productIds");
            ids.forEach(products::add);
            http.send(HttpRequest.newBuilder(URI.create(aiServiceUrl + "/recommendation-audits")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { }
    }
}

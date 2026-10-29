package com.shop.agent.adapter.out.behavior;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Reads only the customer's aggregated behaviour profile from AI Service. */
@ApplicationScoped
public class BehaviorProfileTool {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    @ConfigProperty(name = "shop.ai-service.url") String aiServiceUrl;
    public Profile get(Long userId, String sessionId) {
        if (userId == null && (sessionId == null || sessionId.isBlank())) return new Profile(null, List.of(), 0);
        try {
            String path = userId == null ? "/behavior/profile/session/" + sessionId : "/behavior/profile/" + userId;
            HttpRequest request = HttpRequest.newBuilder(URI.create(aiServiceUrl + path)).timeout(Duration.ofSeconds(5)).GET().build();
            JsonNode json = mapper.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            List<String> ids = new ArrayList<>(); json.path("recentProductIds").forEach(id -> ids.add(id.asText()));
            return new Profile(json.path("latestSearch").asText(null), ids, json.path("interactionCount").asInt());
        } catch (Exception ignored) { return new Profile(null, List.of(), 0); }
    }
    public record Profile(String latestSearch, List<String> recentProductIds, int interactionCount) { }
}

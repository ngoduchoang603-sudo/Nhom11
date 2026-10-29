package com.shop.agent.adapter.out.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Server-side session memory, deliberately capped at 30 minutes by AI Service. */
@ApplicationScoped
public class SessionMemoryTool {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    /** Keeps the active chat responsive while the persistent AI-memory API is temporarily unavailable. */
    private final ConcurrentHashMap<String, Memory> localFallback = new ConcurrentHashMap<>();
    /** Last catalog response in this chat, so "hai máy trên" can be resolved safely. */
    private final ConcurrentHashMap<String, List<Memory>> recentCatalogs = new ConcurrentHashMap<>();
    /** Compact, typed preferences are safer and cheaper than replaying the whole transcript. */
    private final ConcurrentHashMap<String, ShoppingContext> contexts = new ConcurrentHashMap<>();
    @ConfigProperty(name = "shop.ai-service.url") String aiServiceUrl;
    public void save(String sessionId, String productId, String productName) {
        if (sessionId == null || sessionId.isBlank() || productId == null || productId.isBlank()) return;
        localFallback.put(sessionId, new Memory(productId, productName));
        try {
            String body = mapper.createObjectNode().put("sessionId", sessionId).put("productId", productId).put("productName", productName).toString();
            http.send(HttpRequest.newBuilder(URI.create(aiServiceUrl + "/agent-memory")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { }
    }
    public Memory get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        try {
            JsonNode json = mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(aiServiceUrl + "/agent-memory/" + sessionId)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
            if (!json.isObject() || json.path("productId").asText().isBlank()) return localFallback.get(sessionId);
            Memory memory = new Memory(json.path("productId").asText(), json.path("productName").asText());
            localFallback.put(sessionId, memory);
            if (json.hasNonNull("catalogJson") && !json.path("catalogJson").asText().isBlank()) {
                List<Memory> catalog = mapper.readValue(json.path("catalogJson").asText(), mapper.getTypeFactory().constructCollectionType(List.class, Memory.class));
                if (!catalog.isEmpty()) recentCatalogs.put(sessionId, catalog);
            }
            if (json.hasNonNull("contextJson") && !json.path("contextJson").asText().isBlank()) {
                contexts.put(sessionId, mapper.readValue(json.path("contextJson").asText(), ShoppingContext.class));
            }
            return memory;
        } catch (Exception ignored) { return localFallback.get(sessionId); }
    }
    public void saveCatalog(String sessionId, List<Memory> products, ShoppingContext context) {
        if (sessionId == null || sessionId.isBlank() || products == null || products.isEmpty()) return;
        List<Memory> safe = products.stream().filter(p -> p.productId() != null && !p.productId().isBlank()).limit(5).toList();
        if (safe.isEmpty()) return;
        recentCatalogs.put(sessionId, safe);
        localFallback.put(sessionId, safe.getFirst());
        if (context != null && !context.isEmpty()) contexts.put(sessionId, context);
        try {
            String body = mapper.createObjectNode().put("sessionId", sessionId)
                    .put("productId", safe.getFirst().productId()).put("productName", safe.getFirst().productName())
                    .put("catalogJson", mapper.writeValueAsString(safe))
                    .put("contextJson", context == null || context.isEmpty() ? null : mapper.writeValueAsString(context)).toString();
            http.send(HttpRequest.newBuilder(URI.create(aiServiceUrl + "/agent-memory")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { }
    }
    public List<Memory> recentCatalog(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        List<Memory> cached = recentCatalogs.get(sessionId);
        if (cached != null && !cached.isEmpty()) return cached;
        get(sessionId); // reload persisted catalog after a container restart
        return recentCatalogs.getOrDefault(sessionId, List.of());
    }
    public ShoppingContext context(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return ShoppingContext.empty();
        if (!contexts.containsKey(sessionId)) get(sessionId);
        return contexts.getOrDefault(sessionId, ShoppingContext.empty());
    }
    public ShoppingContext updateContext(String sessionId, String question) {
        ShoppingContext updated = context(sessionId).merge(question);
        if (sessionId != null && !sessionId.isBlank() && !updated.isEmpty()) {
            contexts.put(sessionId, updated);
            Memory product = get(sessionId);
            if (product != null) persistContext(sessionId, product, updated);
        }
        return updated;
    }
    private void persistContext(String sessionId, Memory product, ShoppingContext context) {
        try {
            String body = mapper.createObjectNode().put("sessionId", sessionId)
                    .put("productId", product.productId()).put("productName", product.productName())
                    .put("contextJson", mapper.writeValueAsString(context)).toString();
            http.send(HttpRequest.newBuilder(URI.create(aiServiceUrl + "/agent-memory")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { }
    }
    public record Memory(String productId, String productName) { }

    /** Only stable purchase constraints are retained; no raw chat text or secrets are stored. */
    public record ShoppingContext(Long maxBudget, Integer minRamGb, String primaryNeed) {
        private static final Pattern MILLION = Pattern.compile("(?:duoi|dưới|toi da|tối đa|khong qua|không quá)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:trieu|triệu|tr)\\b", Pattern.CASE_INSENSITIVE);
        private static final Pattern RAM = Pattern.compile("\\bram\\s*(\\d{1,2})\\s*gb?\\b", Pattern.CASE_INSENSITIVE);
        public static ShoppingContext empty() { return new ShoppingContext(null, null, null); }
        public boolean isEmpty() { return maxBudget == null && minRamGb == null && (primaryNeed == null || primaryNeed.isBlank()); }
        public ShoppingContext merge(String question) {
            String text = question == null ? "" : question.toLowerCase();
            Long budget = maxBudget; Integer ram = minRamGb; String need = primaryNeed;
            Matcher price = MILLION.matcher(text);
            if (price.find()) {
                try { budget = Math.round(Double.parseDouble(price.group(1).replace(',', '.')) * 1_000_000L); }
                catch (NumberFormatException ignored) { }
            }
            Matcher ramMatcher = RAM.matcher(text);
            if (ramMatcher.find()) {
                try { ram = Integer.valueOf(ramMatcher.group(1)); }
                catch (NumberFormatException ignored) { }
            }
            if (text.contains("chơi game") || text.contains("gaming") || text.contains("hiệu năng")) need = "gaming";
            else if (text.contains("camera") || text.contains("chụp ảnh")) need = "camera";
            else if (text.contains("pin")) need = "battery";
            return new ShoppingContext(budget, ram, need);
        }
        public String enrich(String question) {
            String base = question == null ? "" : question;
            String lower = base.toLowerCase();
            StringBuilder result = new StringBuilder(base);
            if (maxBudget != null && !lower.matches("(?s).*\\d+(?:[.,]\\d+)?\\s*(trieu|triệu|tr).*")) result.append(" dưới ").append(maxBudget / 1_000_000L).append(" triệu");
            if (minRamGb != null && !lower.contains("ram")) result.append(" RAM ").append(minRamGb).append("GB");
            if (primaryNeed != null && !lower.contains(primaryNeed.equals("gaming") ? "game" : primaryNeed.equals("camera") ? "camera" : "pin")) result.append(" ").append(primaryNeed);
            return result.toString();
        }
    }
}

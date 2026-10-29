package com.shop.ai.application;

import com.shop.ai.domain.BehaviorEvent;
import com.shop.ai.domain.RecommendationAudit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class BehaviorService {
    @Transactional
    public void record(Long userId, String sessionId, String eventType, String productId, String productName, String searchQuery) {
        if ((userId == null && (sessionId == null || sessionId.isBlank())) || eventType == null || eventType.isBlank()) return;
        BehaviorEvent event = new BehaviorEvent();
        event.userId = userId; event.sessionId = userId == null ? sessionId : null; event.eventType = eventType; event.productId = productId;
        event.productName = productName; event.searchQuery = searchQuery; event.createdAt = java.time.Instant.now();
        event.persist();
        if (("add_to_cart".equals(eventType) || "order_created".equals(eventType)) && productId != null) {
            String subjectKey = userId != null ? "u:" + userId : "s:" + sessionId;
            RecommendationAudit.<RecommendationAudit>list("subjectKey = ?1 order by createdAt desc", subjectKey).stream()
                    .filter(audit -> audit.productIds.contains("," + productId + ","))
                    .forEach(audit -> { if ("add_to_cart".equals(eventType)) { audit.addedToCart = true; audit.outcome = "ADDED_TO_CART"; } else { audit.ordered = true; audit.outcome = "ORDER_CREATED"; } });
        }
    }

    public Profile profile(Long userId, String sessionId) {
        if (userId == null && (sessionId == null || sessionId.isBlank())) return new Profile(null, null, List.of(), 0);
        List<BehaviorEvent> events = userId != null
                ? BehaviorEvent.list("userId = ?1 order by createdAt desc", userId)
                : BehaviorEvent.list("sessionId = ?1 order by createdAt desc", sessionId);
        Map<String, Long> products = events.stream().filter(e -> e.productId != null)
                .collect(Collectors.groupingBy(e -> e.productId, Collectors.counting()));
        List<String> recent = products.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).toList();
        String search = events.stream().map(e -> e.searchQuery).filter(s -> s != null && !s.isBlank()).findFirst().orElse(null);
        return new Profile(search, null, recent, events.size());
    }
    @Transactional
    public void delete(Long userId, String sessionId) {
        if (userId != null) BehaviorEvent.delete("userId", userId);
        else if (sessionId != null && !sessionId.isBlank()) BehaviorEvent.delete("sessionId", sessionId);
    }
    @Transactional
    public void auditRecommendation(Long userId, String sessionId, List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) return;
        RecommendationAudit audit = new RecommendationAudit();
        audit.subjectKey = userId != null ? "u:" + userId : "s:" + sessionId;
        audit.productIds = "," + String.join(",", productIds) + ",";
        audit.outcome = "DISPLAYED"; audit.addedToCart = false; audit.ordered = false; audit.createdAt = java.time.Instant.now(); audit.persist();
    }
    /** Aggregate only; no customer identity is exposed in the admin conversion report. */
    public RecommendationSummary recommendationSummary() {
        long displayed = RecommendationAudit.count();
        long added = RecommendationAudit.count("addedToCart", true);
        long ordered = RecommendationAudit.count("ordered", true);
        return new RecommendationSummary(displayed, added, ordered,
                displayed == 0 ? 0d : Math.round(added * 1000d / displayed) / 10d,
                displayed == 0 ? 0d : Math.round(ordered * 1000d / displayed) / 10d);
    }
    public record RecommendationSummary(long displayed, long addedToCart, long ordered, double addToCartRate, double orderRate) { }
    public record Profile(String latestSearch, String favoriteBrand, List<String> recentProductIds, int interactionCount) { }
}

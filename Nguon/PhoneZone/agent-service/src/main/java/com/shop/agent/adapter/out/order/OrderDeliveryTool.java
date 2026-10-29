package com.shop.agent.adapter.out.order;

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

/** Read-only REST tool. The agent never queries Order service's database. */
@ApplicationScoped
public class OrderDeliveryTool {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    @ConfigProperty(name = "shop.order-service.url") String orderServiceUrl;
    @ConfigProperty(name = "shop.agent.internal.key") String agentKey;

    public List<OrderInfo> recentOrders(Long userId) { return read("/api/internal/agent/users/" + userId + "/orders", this::order); }
    public List<CartItem> cart(Long userId) { return read("/api/internal/agent/users/" + userId + "/cart", this::cartItem); }
    public List<CouponInfo> coupons(long subtotal) { return read("/api/internal/agent/coupons?subtotal=" + Math.max(0, subtotal), this::coupon); }

    private <T> List<T> read(String path, java.util.function.Function<JsonNode, T> mapperFunction) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(orderServiceUrl + path)).timeout(Duration.ofSeconds(6))
                    .header("X-Agent-Key", agentKey).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return List.of();
            List<T> result = new ArrayList<>();
            for (JsonNode node : mapper.readTree(response.body())) result.add(mapperFunction.apply(node));
            return result;
        } catch (Exception ignored) { return List.of(); }
    }
    private OrderInfo order(JsonNode n) { return new OrderInfo(n.path("id").asLong(), n.path("status").asText(), n.path("total").asLong(), n.path("date").asText(), n.path("paymentMethod").asText()); }
    private CartItem cartItem(JsonNode n) { return new CartItem(n.path("id").asLong(), n.path("productId").asText(), n.path("productName").asText(), n.path("productPrice").asLong(), n.path("quantity").asLong()); }
    private CouponInfo coupon(JsonNode n) { return new CouponInfo(n.path("code").asText(), n.path("name").asText(), n.path("discount").asLong(), n.path("expiresAt").asText()); }
    public record OrderInfo(long id, String status, long total, String date, String paymentMethod) { }
    public record CartItem(long id, String productId, String productName, long productPrice, long quantity) { }
    public record CouponInfo(String code, String name, long discount, String expiresAt) { }
}

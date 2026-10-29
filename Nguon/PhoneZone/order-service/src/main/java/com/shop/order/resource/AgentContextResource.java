package com.shop.order.resource;

import com.shop.order.entity.Cart;
import com.shop.order.entity.Coupon;
import com.shop.order.entity.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.smallrye.common.annotation.Blocking;

import java.time.Instant;
import java.util.List;

/**
 * Minimal, read-only context for the Agent service. This is deliberately not a
 * customer API: calls must carry the shared service key and return only fields
 * the assistant needs to answer a customer question.
 */
@ApplicationScoped
@Path("/api/internal/agent")
@jakarta.ws.rs.Produces(MediaType.APPLICATION_JSON)
public class AgentContextResource {
    @ConfigProperty(name = "shop.agent.internal.key") String agentKey;

    @GET
    @Path("/users/{userId}/orders")
    @Blocking public List<OrderView> orders(@HeaderParam("X-Agent-Key") String key, @PathParam("userId") Long userId) {
        requireKey(key);
        return Order.findByUser(userId).stream()
                .sorted((a, b) -> b.date.compareTo(a.date))
                .limit(8)
                .map(o -> new OrderView(o.id, o.status.name(), o.total, o.date, o.paymentMethod, o.deliveryAddress))
                .toList();
    }

    @GET
    @Path("/users/{userId}/cart")
    @Blocking public List<CartView> cart(@HeaderParam("X-Agent-Key") String key, @PathParam("userId") Long userId) {
        requireKey(key);
        return Cart.findByUser(userId).stream()
                .map(c -> new CartView(c.id, c.productId, c.productName, c.productPrice, c.quantity))
                .toList();
    }

    @GET
    @Path("/coupons")
    @Blocking public List<CouponView> coupons(@HeaderParam("X-Agent-Key") String key, @QueryParam("subtotal") long subtotal) {
        requireKey(key);
        return Coupon.<Coupon>listAll().stream()
                .filter(c -> CouponResource.calculate(c, subtotal) >= 0)
                .map(c -> new CouponView(c.code, c.name, CouponResource.calculate(c, subtotal), c.endAt))
                .toList();
    }

    private void requireKey(String key) {
        if (agentKey == null || agentKey.isBlank() || !agentKey.equals(key)) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }

    public record OrderView(Long id, String status, Long total, Instant date, String paymentMethod, String deliveryAddress) { }
    public record CartView(Long id, String productId, String productName, Long productPrice, Long quantity) { }
    public record CouponView(String code, String name, long discount, Instant expiresAt) { }
}

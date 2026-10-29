package com.shop.order.resource;

import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import com.shop.order.entity.OrderTimelineEvent;
import com.shop.order.service.OrderService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * REST API cho don hang.
 */
@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Order", description = "Quan ly don hang")
public class OrderResource {

    @Inject
    OrderService orderService;

    @Inject
    SecurityIdentity identity;

    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "Lay tat ca don hang")
    public List<Order> getAll() {
        return orderService.getAll();
    }

    @GET
    @Path("/page")
    @RolesAllowed("ADMIN")
    public List<Order> getPage(@QueryParam("page") @DefaultValue("0") int page,
                               @QueryParam("size") @DefaultValue("50") int size) {
        return orderService.getPage(page, size);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "USER", "STAFF"})
    public Order getById(@PathParam("id") Long id) {
        Order order = orderService.getById(id);
        requireOwnOrder(order);
        return order;
    }

    @GET
    @Path("/user/{userId}")
    @RolesAllowed({"ADMIN", "USER"})
    public List<Order> getByUser(@PathParam("userId") Long userId) {
        requireCurrentUser(userId);
        return orderService.getByUser(userId);
    }

    @GET
    @Path("/{id}/timeline")
    @RolesAllowed({"ADMIN", "USER", "STAFF"})
    public List<OrderTimelineEvent> timeline(@PathParam("id") Long id) {
        requireOwnOrder(orderService.getById(id));
        return OrderTimelineEvent.findByOrder(id);
    }

    @POST
    @Path("/checkout")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Dat hang tu gio hang")
    public Response checkout(
            @QueryParam("userId") Long userId,
            @QueryParam("address") String deliveryAddress,
            @QueryParam("paymentMethod") @DefaultValue("COD") String paymentMethod,
            @QueryParam("coupon") String coupon) {
        requireCurrentUser(userId);
        Order order = orderService.checkout(userId, deliveryAddress, paymentMethod, coupon);
        return Response.status(Response.Status.CREATED).entity(order).build();
    }

    @PATCH
    @Path("/{id}/status")
    @RolesAllowed({"ADMIN", "STAFF"})
    @Operation(summary = "Cap nhat trang thai don hang (admin/staff)")
    public Order updateStatus(
            @PathParam("id") Long id,
            @QueryParam("status") OrderStatus status) {
        return orderService.updateStatus(id, status);
    }

    /**
     * Endpoint internal — payment-service goi sau khi nhan IPN tu MoMo.
     * O moi truong production nen bao ve bang internal network (K8s ClusterIP)
     * hoac shared secret/token rieng giua cac service.
     */
    @PATCH
    @Path("/internal/{id}/status")
    @PermitAll
    @Operation(summary = "Internal: payment-service cap nhat trang thai sau thanh toan")
    public Order updateStatusInternal(
            @PathParam("id") Long id,
            @QueryParam("status") OrderStatus status) {
        return orderService.updateStatus(id, status);
    }

    /** Internal entitlement check used by product-service before accepting a review. */
    @GET
    @Path("/internal/users/{userId}/products/{productId}/delivered")
    @PermitAll
    @Operation(summary = "Internal: kiểm tra khách đã mua và nhận sản phẩm")
    public Response hasDeliveredProduct(@PathParam("userId") Long userId, @PathParam("productId") String productId) {
        java.util.List<Order> deliveredOrders = Order.<Order>find("userId = ?1 and status = ?2", userId, OrderStatus.DELIVERED)
                .list();
        boolean purchased = deliveredOrders.stream().anyMatch(order -> order.items.stream()
                .anyMatch(item -> productId.equals(item.productId)));
        return Response.ok(java.util.Map.of("purchased", purchased)).build();
    }

    private void requireOwnOrder(Order order) {
        if (identity.hasRole("ADMIN") || identity.hasRole("STAFF")) return;
        requireCurrentUser(order.userId);
    }

    private void requireCurrentUser(Long userId) {
        if (identity.hasRole("ADMIN")) return;
        Long authenticatedId = authenticatedUserId();
        if (userId == null || !userId.equals(authenticatedId)) {
            throw new ForbiddenException("Bạn không có quyền xem hoặc thao tác đơn hàng của người dùng khác");
        }
    }

    private Long authenticatedUserId() {
        if (identity.getPrincipal() instanceof JsonWebToken token) {
            Object claim = token.getClaim("userId");
            if (claim instanceof Number number) return number.longValue();
            try { return claim == null ? null : Long.valueOf(String.valueOf(claim)); }
            catch (NumberFormatException ignored) { }
        }
        return null;
    }
}

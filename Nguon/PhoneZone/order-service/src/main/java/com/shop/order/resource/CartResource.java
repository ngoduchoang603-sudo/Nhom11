package com.shop.order.resource;

import com.shop.order.entity.Cart;
import com.shop.order.service.CartService;
import jakarta.annotation.security.RolesAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * REST API cho gio hang.
 */
@Path("/api/carts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Cart", description = "Quan ly gio hang")
public class CartResource {

    @Inject
    CartService cartService;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/user/{userId}")
    @RolesAllowed({"USER", "ADMIN"})
    public List<Cart> getByUser(@PathParam("userId") Long userId) {
        requireCurrentUser(userId);
        return cartService.getCartByUser(userId);
    }

    @POST
    @RolesAllowed({"USER", "ADMIN"})
    public Response addToCart(
            @QueryParam("userId") Long userId,
            @QueryParam("productId") String productId,
            @QueryParam("quantity") Long quantity,
            @QueryParam("variantId") Long variantId) {
        requireCurrentUser(userId);
        Cart cart = cartService.addToCart(userId, productId, quantity, variantId);
        return Response.status(Response.Status.CREATED).entity(cart).build();
    }

    @PUT
    @Path("/{cartId}")
    @RolesAllowed({"USER", "ADMIN"})
    public Cart updateQuantity(
            @PathParam("cartId") Long cartId,
            @QueryParam("quantity") Long quantity) {
        requireOwnCart(cartId);
        return cartService.updateQuantity(cartId, quantity);
    }

    @DELETE
    @Path("/{cartId}")
    @RolesAllowed({"USER", "ADMIN"})
    public Response remove(@PathParam("cartId") Long cartId) {
        requireOwnCart(cartId);
        cartService.removeFromCart(cartId);
        return Response.noContent().build();
    }

    private void requireOwnCart(Long cartId) {
        if (identity.hasRole("ADMIN")) return;
        Cart cart = Cart.findById(cartId);
        if (cart == null) throw new NotFoundException("Cart item khong ton tai: " + cartId);
        requireCurrentUser(cart.userId);
    }

    private void requireCurrentUser(Long userId) {
        if (identity.hasRole("ADMIN")) return;
        Long authenticatedId = authenticatedUserId();
        if (userId == null || !userId.equals(authenticatedId)) {
            throw new ForbiddenException("Bạn không có quyền thao tác giỏ hàng của người dùng khác");
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

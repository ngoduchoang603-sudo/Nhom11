package com.shop.order.service;

import com.shop.order.client.ProductClient;
import com.shop.order.entity.Cart;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Map;
import java.time.Instant;

/**
 * CartService — quan ly gio hang.
 */
@ApplicationScoped
public class CartService {

    @RestClient
    ProductClient productClient;

    public List<Cart> getCartByUser(Long userId) {
        return Cart.findByUser(userId);
    }

    @Transactional
    public Cart addToCart(Long userId, String productId, Long quantity, Long variantId) {
        // Goi sang product-service de lay thong tin san pham
        Map<String, Object> product = productClient.getProduct(productId);
        long stock = product.get("stock") instanceof Number number ? number.longValue() : 0L;
        Map<String, Object> variant = null;
        if (variantId != null) {
            variant = productClient.getVariants(productId).stream()
                    .filter(v -> v.get("id") instanceof Number n && n.longValue() == variantId).findFirst().orElse(null);
            if (variant == null) throw new WebApplicationException("Phiên bản đã chọn không còn tồn tại", Response.Status.CONFLICT);
            stock = variant.get("stock") instanceof Number number ? number.longValue() : 0L;
        }
        if (quantity == null || quantity < 1 || stock < quantity) {
            throw new WebApplicationException("Sản phẩm đã hết hàng hoặc số lượng vượt tồn kho", Response.Status.CONFLICT);
        }

        // Neu user da co san pham nay trong gio thi cong them quantity
        Cart existing = variantId == null ? Cart.findByUserAndProduct(userId, productId) : Cart.findByUserAndVariant(userId, productId, variantId);
        if (existing != null) {
            if (existing.quantity + quantity > stock) {
                throw new WebApplicationException("Số lượng trong giỏ vượt tồn kho hiện tại", Response.Status.CONFLICT);
            }
            existing.quantity += quantity;
            return existing;
        }

        Cart cart = new Cart();
        cart.userId = userId;
        cart.productId = productId;
        cart.variantId = variantId;
        if (variant != null) cart.variantLabel = String.join(" · ", java.util.stream.Stream.of(variant.get("color"), variant.get("ram"), variant.get("storage"))
                .filter(java.util.Objects::nonNull).map(String::valueOf).filter(s -> !s.isBlank()).toList());
        cart.productName = (String) product.get("name");
        cart.productPrice = product.get("price") != null
                ? ((Number) product.get("price")).longValue() : 0L;
        cart.productImage = (String) product.get("image");
        cart.quantity = quantity;
        cart.createdAt = Instant.now();
        cart.persist();
        return cart;
    }

    @Transactional
    public Cart updateQuantity(Long cartId, Long quantity) {
        if (quantity == null || quantity < 1) {
            throw new WebApplicationException("Số lượng phải lớn hơn 0", Response.Status.BAD_REQUEST);
        }
        Cart cart = Cart.findById(cartId);
        if (cart == null) {
            throw new NotFoundException("Cart item khong ton tai: " + cartId);
        }
        long stock = 0;
        boolean stockChecked = false;
        try {
            if (cart.variantId != null) {
                Map<String, Object> variant = productClient.getVariants(cart.productId).stream()
                        .filter(v -> v.get("id") instanceof Number n && n.longValue() == cart.variantId)
                        .findFirst().orElse(null);
                stock = variant != null && variant.get("stock") instanceof Number number ? number.longValue() : 0L;
            } else {
                Map<String, Object> product = productClient.getProduct(cart.productId);
                stock = product != null && product.get("stock") instanceof Number number ? number.longValue() : 0L;
            }
            stockChecked = true;
        } catch (Exception e) {
            // If we can't reach product-service, allow the update with a warning
        }
        if (stockChecked && quantity > stock) {
            throw new WebApplicationException(
                    "Số lượng yêu cầu (" + quantity + ") vượt tồn kho hiện tại (" + stock + ")",
                    Response.Status.CONFLICT);
        }
        cart.quantity = quantity;
        return cart;
    }

    @Transactional
    public void removeFromCart(Long cartId) {
        boolean deleted = Cart.deleteById(cartId);
        if (!deleted) {
            throw new NotFoundException("Cart item khong ton tai: " + cartId);
        }
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart.delete("userId", userId);
    }
}

package com.shop.order.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;
import java.time.Instant;

/**
 * Cart — gio hang. Mot dong = mot san pham trong gio cua mot user.
 * Luu o PostgreSQL.
 */
@Entity
@Table(name = "cart")
public class Cart extends PanacheEntity {

    public Long quantity = 0L;

    @Column(name = "user_id")
    public Long userId;

    @Column(name = "product_id", length = 50)
    public String productId;

    /** Selected sellable variant. Null keeps compatibility with legacy cart rows. */
    @Column(name = "variant_id")
    public Long variantId;

    @Column(name = "variant_label", length = 160)
    public String variantLabel;

    /** Cache thong tin san pham */
    @Column(name = "product_name")
    public String productName;

    @Column(name = "product_price")
    public Long productPrice;

    @Column(name = "product_image")
    public String productImage;

    @Column(name = "created_at")
    public Instant createdAt;

    public static List<Cart> findByUser(Long userId) {
        return list("userId", userId);
    }

    public static Cart findByUserAndProduct(Long userId, String productId) {
        return find("userId = ?1 and productId = ?2", userId, productId).firstResult();
    }

    public static Cart findByUserAndVariant(Long userId, String productId, Long variantId) {
        return find("userId = ?1 and productId = ?2 and variantId = ?3", userId, productId, variantId).firstResult();
    }
}

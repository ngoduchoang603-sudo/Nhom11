package com.shop.product.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import java.util.List;

/** A sellable version of a phone. Inventory belongs here, not to a display colour string. */
@Entity
@Table(name = "product_variants", uniqueConstraints = @UniqueConstraint(name = "uk_variant_product_sku", columnNames = {"product_id", "sku"}),
        indexes = {@Index(name = "idx_variant_product", columnList = "product_id"), @Index(name = "idx_variant_stock", columnList = "stock")})
public class ProductVariant extends PanacheEntity {
    @Version public Long version;
    @Column(name = "product_id", nullable = false) public Long productId;
    @Column(nullable = false, length = 100) public String sku;
    @Column(length = 60) public String color;
    @Column(length = 40) public String ram;
    @Column(length = 40) public String storage;
    @Min(0) @Column(nullable = false) public Long stock;
    /** Optional delta from product base price, in VND. */
    @Column(name = "price_adjustment", nullable = false) public Long priceAdjustment = 0L;

    public static List<ProductVariant> findByProduct(Long productId) {
        return list("productId = ?1 order by id", productId);
    }
}

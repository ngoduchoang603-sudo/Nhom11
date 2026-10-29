package com.shop.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

/**
 * Item — mot dong trong don hang. Luu vao PostgreSQL.
 * <p>
 * Vi Product nam o microservice khac nen ta khong dung @ManyToOne
 * den Product. Thay vao do luu lai productId (String)
 * va cache mot vai truong hien thi.
 */
@Entity
@Table(name = "items")
public class Item extends PanacheEntity {

    public Long quantity;
    public Long price;
    public Long discount;
    public Long total;

    /** ID cua product tu product-service */
    @Column(name = "product_id", nullable = false, length = 50)
    public String productId;

    @Column(name = "variant_id")
    public Long variantId;

    @Column(name = "variant_label", length = 160)
    public String variantLabel;

    /** Cache thong tin san pham de hien thi nhanh */
    @Column(name = "product_name")
    public String productName;

    @Column(name = "product_image")
    public String productImage;

    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnore  // tranh vong lap khi serialize: Order -> Item -> Order
    public Order order;
}

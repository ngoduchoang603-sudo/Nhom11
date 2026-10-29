package com.shop.order.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order — luu vao PostgreSQL, bang "orders" (vi "order" la tu khoa SQL).
 * <p>
 * Demo dung Hibernate Panache voi PostgreSQL — cau hinh chi khac
 * o JDBC URL va dialect, code Java thi gan nhu giong het MySQL.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_date", columnList = "user_id, order_date"),
        @Index(name = "idx_orders_status_date", columnList = "status, order_date")
})
public class Order extends PanacheEntity {

    @Column(name = "total_price")
    public Long total = 0L;

    @Column(name = "quantity_product")
    public Long quantity = 0L;

    @Column(name = "order_date")
    public Instant date;

    /** ID cua user — vi user nam o service khac (MySQL) nen khong dung @ManyToOne */
    @Column(name = "user_id", nullable = false)
    public Long userId;

    /** Cache mot so thong tin user de hien thi nhanh khong can goi sang user-service */
    @Column(name = "user_name")
    public String userName;

    @Column(name = "delivery_address", length = 500)
    public String deliveryAddress;

    /** Phuong thuc thanh toan: COD (tien mat khi nhan hang) hoac MOMO */
    @Column(name = "payment_method", length = 20)
    public String paymentMethod = "COD";

    @Column(name = "coupon_code", length = 40)
    public String couponCode;

    @Column(name = "coupon_discount")
    public Long couponDiscount = 0L;

    /** Trang thai: PENDING, CONFIRMED, SHIPPING, DELIVERED, CANCELLED */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    public OrderStatus status = OrderStatus.PENDING;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    public List<Item> items = new ArrayList<>();

    public static List<Order> findByUser(Long userId) {
        return list("userId", userId);
    }

    public static List<Order> findByStatus(OrderStatus status) {
        return list("status", status);
    }
}

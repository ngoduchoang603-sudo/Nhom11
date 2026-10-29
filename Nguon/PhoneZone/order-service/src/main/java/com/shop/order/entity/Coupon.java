package com.shop.order.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** Promotion aggregate owned by Order service because it is applied at checkout. */
@Entity
@Table(name = "coupons")
public class Coupon extends PanacheEntity {
    @Column(nullable = false, unique = true, length = 40) public String code;
    @Column(length = 120) public String name;
    /** PERCENT or FIXED (VND). */
    @Column(nullable = false, length = 12) public String type = "PERCENT";
    @Column(nullable = false) public Long value;
    @Column(name = "min_order_value") public Long minOrderValue = 0L;
    @Column(name = "start_at") public Instant startAt;
    @Column(name = "end_at") public Instant endAt;
    @Column(nullable = false) public Boolean active = true;
    @Column(name = "usage_limit") public Long usageLimit;
    @Column(name = "used_count", nullable = false) public Long usedCount = 0L;

    public static Coupon findByCode(String code) { return find("lower(code)", code.toLowerCase()).firstResult(); }
}

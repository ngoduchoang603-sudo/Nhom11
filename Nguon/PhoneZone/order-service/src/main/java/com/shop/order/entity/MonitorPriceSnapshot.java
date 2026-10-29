package com.shop.order.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Last observed selling price, used only by the monitoring job to detect a later price drop. */
@Entity
@Table(name = "monitor_price_snapshots")
public class MonitorPriceSnapshot extends PanacheEntity {
    @Column(name = "product_id", nullable = false, unique = true, length = 80) public String productId;
    @Column(name = "product_name", nullable = false, length = 250) public String productName;
    @Column(name = "last_price", nullable = false) public Long lastPrice;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
}

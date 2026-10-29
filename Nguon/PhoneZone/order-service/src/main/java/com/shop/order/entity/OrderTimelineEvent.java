package com.shop.order.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

/** Immutable delivery/status milestones. A real timestamp is saved only when a status changes. */
@Entity
@Table(name = "order_timeline_events", indexes = @Index(name = "idx_timeline_order_time", columnList = "order_id,event_time"))
public class OrderTimelineEvent extends PanacheEntity {
    @Column(name = "order_id", nullable = false) public Long orderId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public OrderStatus status;
    @Column(name = "event_time", nullable = false) public Instant eventTime;
    @Column(length = 200) public String note;

    public static List<OrderTimelineEvent> findByOrder(Long orderId) {
        return list("orderId = ?1 order by eventTime asc", orderId);
    }
}

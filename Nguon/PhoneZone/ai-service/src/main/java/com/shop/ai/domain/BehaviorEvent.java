package com.shop.ai.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Anonymous or authenticated storefront interaction, owned by the AI bounded context. */
@Entity
@Table(name = "behavior_events")
public class BehaviorEvent extends PanacheEntity {
    /** One of userId/sessionId is present. Anonymous sessions never store an identity. */
    public Long userId;
    @Column(length = 80) public String sessionId;
    @Column(nullable = false, length = 40) public String eventType;
    @Column(length = 80) public String productId;
    @Column(length = 120) public String productName;
    @Column(length = 300) public String searchQuery;
    @Column(nullable = false) public Instant createdAt;
}

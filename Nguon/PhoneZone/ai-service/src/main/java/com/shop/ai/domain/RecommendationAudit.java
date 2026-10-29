package com.shop.ai.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Feedback-loop audit: what was recommended and whether the customer engaged. */
@Entity
@Table(name = "recommendation_audits")
public class RecommendationAudit extends PanacheEntity {
    @Column(nullable = false, length = 100) public String subjectKey;
    @Column(nullable = false, length = 500) public String productIds;
    @Column(nullable = false, length = 30) public String outcome;
    @Column(nullable = false) public boolean addedToCart;
    @Column(nullable = false) public boolean ordered;
    @Column(nullable = false) public Instant createdAt;
}

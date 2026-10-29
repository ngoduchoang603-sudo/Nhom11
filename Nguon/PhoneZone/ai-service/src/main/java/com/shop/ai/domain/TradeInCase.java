package com.shop.ai.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** Trade-in is only an estimate until a staff member inspects the handset in store. */
@Entity
@Table(name = "trade_in_cases")
public class TradeInCase extends PanacheEntity {
    @Column(nullable = false) public Long userId;
    @Column(nullable = false, length = 120) public String deviceName;
    @Column(nullable = false, length = 80) public String storeId;
    @Column(nullable = false, length = 30) public String status; // PENDING, VERIFIED, COMPLETED, REJECTED
    @Column(nullable = false) public long estimatedMin;
    @Column(nullable = false) public long estimatedMax;
    public Long verifiedPrice;
    @Column(length = 1000) public String staffNote;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> images;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> visionResult;
    @Column(nullable = false) public Instant createdAt;
    @Column(nullable = false) public Instant updatedAt;
}

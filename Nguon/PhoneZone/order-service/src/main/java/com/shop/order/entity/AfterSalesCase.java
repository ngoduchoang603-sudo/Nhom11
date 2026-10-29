package com.shop.order.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;

/** Hậu mãi phải gắn với một thiết bị/IMEI, không chỉ với đơn hàng chung. */
@Entity
@Table(name = "after_sales_cases", indexes = {
        @Index(name = "idx_after_sales_order", columnList = "order_id"),
        @Index(name = "idx_after_sales_imei", columnList = "imei"),
        @Index(name = "idx_after_sales_status", columnList = "status") })
public class AfterSalesCase extends PanacheEntity {
    @Column(name = "case_code", unique = true, nullable = false, length = 40) public String caseCode;
    @Column(name = "order_id", nullable = false) public Long orderId;
    @Column(name = "user_id", nullable = false) public Long userId;
    @Column(name = "product_id", length = 64) public String productId;
    @Column(name = "product_name", length = 250) public String productName;
    @Column(nullable = false, length = 80) public String imei;
    /** RETURN or WARRANTY. */ @Column(name = "request_type", nullable = false, length = 20) public String requestType;
    @Column(name = "reason_code", length = 40) public String reasonCode;
    @Column(length = 2000) public String description;
    /** One URL per line; actual files belong in object storage, never in PostgreSQL. */
    @Column(name = "evidence_urls", length = 4000) public String evidenceUrls;
    @Column(name = "warranty_start") public Instant warrantyStart;
    @Column(name = "warranty_end") public Instant warrantyEnd;
    @Column(name = "return_deadline") public Instant returnDeadline;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) public AfterSalesStatus status = AfterSalesStatus.CREATED;
    @Column(name = "inspection_result", length = 2000) public String inspectionResult;
    @Column(name = "resolution_note", length = 2000) public String resolutionNote;
    @Column(name = "staff_note", length = 2000) public String staffNote;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at") public Instant updatedAt;
    @PrePersist void create() { if (createdAt == null) createdAt = Instant.now(); if (caseCode == null) caseCode = "RMA-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase(); }
    @PreUpdate void update() { updatedAt = Instant.now(); }
}

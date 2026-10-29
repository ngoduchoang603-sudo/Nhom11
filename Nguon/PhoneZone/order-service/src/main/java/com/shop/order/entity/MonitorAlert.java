package com.shop.order.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Read-only operational alert; never triggers a business mutation. */
@Entity @Table(name="monitor_alerts", indexes=@Index(name="idx_monitor_open", columnList="resolved,updated_at"))
public class MonitorAlert extends PanacheEntity {
    @Column(nullable=false, unique=true, length=120) public String fingerprint;
    @Column(nullable=false, length=40) public String type;
    @Column(nullable=false, length=12) public String severity;
    @Column(nullable=false, length=500) public String message;
    @Column(nullable=false) public boolean resolved;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;
}

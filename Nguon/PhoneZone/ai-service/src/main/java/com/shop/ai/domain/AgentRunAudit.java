package com.shop.ai.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Internal-only observability record. It deliberately stores no secrets or payment data. */
@Entity @Table(name = "agent_run_audits")
public class AgentRunAudit extends PanacheEntity {
    @Column(nullable = false, length = 100) public String sessionId;
    @Column(length = 40) public String route;
    @Column(nullable = false) public long latencyMs;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) public List<String> tools;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) public Map<String, Object> tokenUsage;
    @Column(nullable = false) public Instant createdAt;
}

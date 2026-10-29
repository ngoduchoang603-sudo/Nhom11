package com.shop.ai.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/** Short-lived, server-side shopping context. It expires after 30 minutes. */
@Entity
@Table(name = "agent_memories")
public class AgentMemory extends PanacheEntity {
    @Column(nullable = false, unique = true, length = 100) public String sessionId;
    @Column(nullable = false, length = 80) public String productId;
    @Column(nullable = false, length = 160) public String productName;
    /** Last product cards shown in this conversation. Kept as JSONB so references such as
     * "hai máy trên" survive an Agent container restart. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") public String catalogJson;
    /** Extracted shopping preferences, kept separately from product cards so
     * follow-up questions can retain a budget or priority without replaying chat. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") public String contextJson;
    @Column(nullable = false) public Instant expiresAt;
}

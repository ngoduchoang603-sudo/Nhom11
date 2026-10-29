package com.shop.ai.adapter.in.rest;

import com.shop.ai.domain.AgentRunAudit;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.List;

/** Admin-only observability. No messages, tokens, addresses or payment data are exposed. */
@Path("/admin/agent-runs")
@Produces(MediaType.APPLICATION_JSON)
public class AgentAuditResource {
    @GET @Blocking @RolesAllowed("ADMIN")
    public List<RunView> recent(@QueryParam("limit") Integer requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit == null ? 100 : requestedLimit, 1), 200);
        return AgentRunAudit.<AgentRunAudit>find("order by createdAt desc").page(0, limit).list().stream()
                .map(a -> new RunView(a.id, a.route, a.tools, a.latencyMs, a.tokenUsage, a.createdAt))
                .toList();
    }
    public record RunView(Long id, String route, List<String> tools, long latencyMs,
                          java.util.Map<String, Object> usage, Instant createdAt) { }
}

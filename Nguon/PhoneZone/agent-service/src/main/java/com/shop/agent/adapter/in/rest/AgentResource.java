package com.shop.agent.adapter.in.rest;

import com.shop.agent.application.OrchestratorAgent;
import com.shop.agent.application.ShoppingAgent;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.Blocking;

/** Internal API called by ai-service only; intentionally not exposed through Kong. */
@Path("/plan") @Produces(MediaType.APPLICATION_JSON)
public class AgentResource {
    @Inject OrchestratorAgent agent;
    // Product/Order tools use bounded synchronous HTTP. Keep them off Vert.x's
    // event loop so concurrent chats cannot block one another.
    @POST @Blocking public ShoppingAgent.AgentResult plan(@Valid Request request) { return agent.plan(request.sessionId(), request.userId(), request.question()); }
    public record Request(String sessionId, Long userId, @NotBlank String question) { }
}

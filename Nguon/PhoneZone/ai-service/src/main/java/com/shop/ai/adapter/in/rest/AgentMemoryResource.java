package com.shop.ai.adapter.in.rest;

import com.shop.ai.application.AgentMemoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Internal short-term context API used by agent-service. */
@Path("/agent-memory") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class AgentMemoryResource {
    @Inject AgentMemoryService memory;
    @GET @Path("/{sessionId}") public AgentMemoryService.Memory get(@PathParam("sessionId") String sessionId) { return memory.get(sessionId); }
    @POST public void save(Request request) { memory.save(request.sessionId(), request.productId(), request.productName(), request.catalogJson(), request.contextJson()); }
    public record Request(String sessionId, String productId, String productName, String catalogJson, String contextJson) { }
}

package com.shop.ai.adapter.in.rest;

import com.shop.ai.application.BehaviorService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/recommendation-audits") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class RecommendationAuditResource {
    @Inject BehaviorService behavior;
    @POST public void create(Request request) { behavior.auditRecommendation(request.userId(), request.sessionId(), request.productIds()); }
    @GET @Path("/summary") public BehaviorService.RecommendationSummary summary() { return behavior.recommendationSummary(); }
    public record Request(Long userId, String sessionId, List<String> productIds) { }
}

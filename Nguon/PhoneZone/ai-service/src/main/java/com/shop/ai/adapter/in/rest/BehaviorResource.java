package com.shop.ai.adapter.in.rest;

import com.shop.ai.application.BehaviorService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/behavior")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BehaviorResource {
    @Inject BehaviorService behavior;
    @POST @Path("/events") public void event(@Valid EventRequest request) {
        if (!request.consent()) return;
        behavior.record(request.userId(), request.sessionId(), request.eventType(), request.productId(), request.productName(), request.searchQuery());
    }
    @GET @Path("/profile/{userId}") public BehaviorService.Profile profile(@PathParam("userId") Long userId) { return behavior.profile(userId, null); }
    @GET @Path("/profile/session/{sessionId}") public BehaviorService.Profile anonymousProfile(@PathParam("sessionId") String sessionId) { return behavior.profile(null, sessionId); }
    @DELETE @Path("/data") public void delete(@jakarta.ws.rs.QueryParam("userId") Long userId, @jakarta.ws.rs.QueryParam("sessionId") String sessionId) { behavior.delete(userId, sessionId); }
    public record EventRequest(Long userId, String sessionId, boolean consent, @NotBlank String eventType, String productId, String productName, String searchQuery) { }
}

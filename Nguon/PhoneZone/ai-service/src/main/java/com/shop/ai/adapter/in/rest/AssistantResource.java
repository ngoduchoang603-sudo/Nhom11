package com.shop.ai.adapter.in.rest;

import com.shop.ai.application.port.in.AskAssistant;
import jakarta.inject.Inject;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/chat") @Produces(MediaType.APPLICATION_JSON)
public class AssistantResource {
    @Inject AskAssistant assistant;
    @Inject SecurityIdentity identity;
    @POST public AskAssistant.Answer chat(@Valid Request request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank() ? UUID.randomUUID().toString() : request.sessionId();
        // Never trust a userId supplied by browser JSON. Anonymous visitors may
        // still use generic catalog chat, but account/order/cart tools require
        // an authenticated token whose userId claim is verified by Quarkus.
        return assistant.ask(sessionId, authenticatedUserId(), request.message());
    }
    private Long authenticatedUserId() {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken token)) return null;
        Object claim = token.getClaim("userId");
        if (claim instanceof Number number) return number.longValue();
        try { return claim == null ? null : Long.parseLong(String.valueOf(claim)); }
        catch (NumberFormatException ignored) { return null; }
    }
    public record Request(String sessionId, Long userId, @NotBlank String message) { }
}

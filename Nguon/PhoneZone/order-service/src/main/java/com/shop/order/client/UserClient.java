package com.shop.order.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

/**
 * Goi sang user-service de lay thong tin user.
 */
@RegisterRestClient(configKey = "user-client")
@Path("/api/users")
public interface UserClient {

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(3000)
    @Retry(maxRetries = 2, delay = 500)
    Map<String, Object> getUser(@PathParam("id") Long id);
}

package com.shop.product.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.Map;

@RegisterRestClient(configKey = "order-client")
public interface OrderClient {
    @GET @Path("/api/orders/internal/users/{userId}/products/{productId}/delivered")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> hasDeliveredProduct(@PathParam("userId") Long userId, @PathParam("productId") String productId);
}

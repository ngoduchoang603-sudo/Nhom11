package com.shop.payment.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

@RegisterRestClient(configKey = "zalopay-client")
public interface ZaloPayClient {
    @POST @Path("/v2/create") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
    @Timeout(30000) @Retry(maxRetries = 2, delay = 1000)
    Map<String, Object> create(Map<String, Object> request);

    @POST @Path("/v2/query") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
    @Timeout(10000) @Retry(maxRetries = 2, delay = 500)
    Map<String, Object> query(Map<String, Object> request);
}

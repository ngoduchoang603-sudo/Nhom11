package com.shop.payment.client;

import com.shop.payment.dto.OrderStatusUpdate;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Client de goi sang order-service cap nhat trang thai don hang
 * khi thanh toan thanh cong/that bai.
 */
@RegisterRestClient(configKey = "order-client")
@Path("/api/orders")
public interface OrderClient {

    @PATCH
    @Path("/internal/{id}/status")
    @Timeout(3000)
    @Retry(maxRetries = 3, delay = 500)
    void updateStatus(@PathParam("id") Long orderId, @QueryParam("status") String status);
}

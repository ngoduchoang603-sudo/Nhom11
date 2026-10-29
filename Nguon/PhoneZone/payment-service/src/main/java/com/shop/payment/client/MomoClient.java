package com.shop.payment.client;

import com.shop.payment.dto.MomoCreateRequest;
import com.shop.payment.dto.MomoCreateResponse;
import com.shop.payment.dto.MomoQueryRequest;
import com.shop.payment.dto.MomoQueryResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST Client de goi MoMo sandbox API.
 * <p>
 * Base URL: cau hinh trong application.properties (momo.endpoint).
 */
@RegisterRestClient(configKey = "momo-client")
@Path("/v2/gateway/api")
public interface MomoClient {

    /**
     * Tao giao dich thanh toan, MoMo tra ve payUrl + qrCodeUrl + deeplink.
     * <p>
     * @Timeout 30s theo khuyen cao cua MoMo
     */
    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(value = 30000)
    @Retry(maxRetries = 2, delay = 1000)
    MomoCreateResponse create(MomoCreateRequest request);

    /**
     * Kiem tra trang thai giao dich.
     */
    @POST
    @Path("/query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(value = 10000)
    @Retry(maxRetries = 2, delay = 500)
    MomoQueryResponse query(MomoQueryRequest request);
}

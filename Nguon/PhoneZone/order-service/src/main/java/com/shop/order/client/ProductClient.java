package com.shop.order.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;
import java.util.List;

/**
 * MicroProfile REST Client — goi sang product-service.
 * <p>
 * Diem hay cua Quarkus: chi can dinh nghia interface, framework tu sinh
 * proxy. Khong can RestTemplate hay WebClient nhu Spring.
 * <p>
 * URL duoc cau hinh trong application.properties:
 *   quarkus.rest-client."com.shop.order.client.ProductClient".url=...
 * <p>
 * Co them annotation Fault Tolerance:
 *   - @Timeout: ngat sau N giay neu service kia treo
 *   - @Retry: thu lai N lan neu loi
 *   - @Fallback: ham thay the khi het retry van loi
 */
@RegisterRestClient(configKey = "product-client")
@Path("/api/products")
public interface ProductClient {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(3000)
    @Fallback(fallbackMethod = "fallbackProducts")
    List<Map<String, Object>> getProducts();

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(3000)
    @Retry(maxRetries = 2, delay = 500)
    @Fallback(fallbackMethod = "fallbackProduct")
    Map<String, Object> getProduct(@PathParam("id") String id);

    @GET
    @Path("/{id}/variants")
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(3000)
    @Retry(maxRetries = 2, delay = 300)
    @Fallback(fallbackMethod = "fallbackVariants")
    List<Map<String, Object>> getVariants(@PathParam("id") String id);

    @POST
    @Path("/{id}/decrease-stock")
    @Timeout(3000)
    @Retry(maxRetries = 3, delay = 500)
    void decreaseStock(@PathParam("id") String id, @QueryParam("quantity") long quantity);

    @POST
    @Path("/{id}/decrease-stock")
    @Timeout(3000)
    @Retry(maxRetries = 3, delay = 500)
    void decreaseVariantStock(@PathParam("id") String id, @QueryParam("variantId") Long variantId, @QueryParam("quantity") long quantity);

    /** Phuong thuc fallback khi product-service khong phan hoi */
    default Map<String, Object> fallbackProduct(String id) {
        return Map.of(
                "id", id,
                "name", "[Khong lay duoc thong tin san pham]",
                "price", 0,
                "stock", 0
        );
    }
    default List<Map<String, Object>> fallbackProducts() { return List.of(); }
    default List<Map<String, Object>> fallbackVariants(String id) { return List.of(); }
}

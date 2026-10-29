package com.shop.product.resource;

import com.shop.product.dto.ProductSearchRequest;
import com.shop.product.dto.CreateCommentRequest;
import com.shop.product.entity.Product;
import com.shop.product.service.ProductService;
import com.shop.product.client.OrderClient;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import org.jboss.logging.Logger;

/**
 * REST API cho Product service.
 */
@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Product", description = "Quan ly san pham")
public class ProductResource {

    private static final Logger LOG = Logger.getLogger(ProductResource.class);

    @Inject
    ProductService productService;

    @Inject
    JsonWebToken jwt;

    @Inject @RestClient
    OrderClient orderClient;

    @GET
    @PermitAll
    @Operation(summary = "Lay tat ca san pham")
    public List<Product> getAll() {
        return productService.findAll();
    }

    @GET
    @Path("/page")
    @PermitAll
    @Operation(summary = "Lay san pham theo trang, toi da 100 ban ghi/trang")
    public List<Product> getPage(@QueryParam("page") @DefaultValue("0") int page,
                                 @QueryParam("size") @DefaultValue("24") int size) {
        return productService.findPage(page, size);
    }

    @GET
    @Path("/count")
    @PermitAll
    @Operation(summary = "Dem so san pham active")
    public long count() {
        return productService.count();
    }

    @GET
    @Path("/{id}")
    @PermitAll
    @Operation(summary = "Lay san pham theo id")
    public Product getById(@PathParam("id") String id) {
        return productService.findById(id);
    }

    @GET
    @Path("/newest")
    @PermitAll
    @Operation(summary = "Top 8 san pham moi nhat")
    public List<Product> getNewest() {
        return productService.getNewest();
    }

    @GET
    @Path("/sale")
    @PermitAll
    @Operation(summary = "Top 8 san pham co khuyen mai")
    public List<Product> getOnSale() {
        return productService.getOnSale();
    }

    @POST
    @Path("/search")
    @PermitAll
    @Operation(summary = "Tim kiem san pham co loc theo gia, thuong hieu, ...")
    public List<Product> search(ProductSearchRequest req) {
        return productService.search(req);
    }

    @POST
    @RolesAllowed({"ADMIN", "STAFF"})
    @Operation(summary = "Tao san pham moi")
    public Response create(@Valid Product product) {
        Product created = productService.create(product);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "STAFF"})
    @Operation(summary = "Cap nhat san pham")
    public Response update(@PathParam("id") String id, Product product) {
        productService.update(id, product);
        // Giao dien chi can biet thao tac thanh cong. Tra 204 tranh serialize lai
        // entity sau transaction (co collection comments lazy) va gay loi 500.
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Xoa san pham (soft delete)")
    public Response delete(@PathParam("id") String id) {
        productService.delete(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/comments")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Them binh luan cho san pham")
    @Transactional
    public Product addComment(
            @PathParam("id") String id,
            CreateCommentRequest request) {
        if (request == null || request.message == null || request.message.isBlank()) {
            throw new BadRequestException("Vui lòng nhập nội dung đánh giá");
        }
        if (request.message.length() > 1000 || request.rating == null || request.rating < 1 || request.rating > 5) {
            throw new BadRequestException("Đánh giá cần có từ 1 đến 5 sao");
        }
        Long userId = parseUserId(jwt.getClaim("userId"));
        if (userId == null) {
            userId = parseUserId(jwt.getSubject());
        }
        if (userId == null) {
            LOG.warnf("Từ chối đánh giá sản phẩm %s: token không có userId", id);
            throw new BadRequestException("Token khong co userId hop le");
        }
        LOG.infof("Kiểm tra quyền đánh giá: user=%d, product=%s", userId, id);
        try {
            Object purchased = orderClient.hasDeliveredProduct(userId, id).get("purchased");
            if (!Boolean.parseBoolean(String.valueOf(purchased))) {
                throw new ForbiddenException("Chỉ khách đã mua và nhận sản phẩm mới được đánh giá");
            }
        } catch (ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException("Chưa thể kiểm tra quyền đánh giá, vui lòng thử lại");
        }
        return productService.addComment(id, userId, jwt.getName(), request.message, request.rating);
    }

    /** SmallRye JWT co the tra custom claim dang Number hoac String tuy theo token. */
    private Long parseUserId(Object claim) {
        if (claim instanceof Number number) {
            return number.longValue();
        }
        if (claim instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Endpoint internal cho order-service goi qua REST client de tru kho */
    @POST
    @Path("/{id}/decrease-stock")
    @PermitAll
    @Operation(summary = "Tru kho khi co don hang (goi tu order-service)")
    public Response decreaseStock(
            @PathParam("id") String id,
            @QueryParam("quantity") long quantity,
            @QueryParam("variantId") Long variantId) {
        if (variantId != null) productService.decreaseVariantStock(id, variantId, quantity);
        else productService.decreaseStock(id, quantity);
        return Response.ok().build();
    }

}

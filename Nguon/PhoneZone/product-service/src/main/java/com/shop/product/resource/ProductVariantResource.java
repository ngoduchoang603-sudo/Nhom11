package com.shop.product.resource;

import com.shop.product.entity.Product;
import com.shop.product.entity.ProductVariant;
import com.shop.product.service.ProductService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/** Variant inventory endpoint. Customer reads it; staff/admin manage it. */
@Path("/api/products/{productId}/variants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductVariantResource {
    @Inject
    ProductService productService;

    @GET @PermitAll
    public List<ProductVariant> list(@PathParam("productId") Long productId) {
        return ProductVariant.findByProduct(productId);
    }

    @POST @RolesAllowed({"ADMIN", "STAFF"}) @Transactional
    public Response create(@PathParam("productId") Long productId, ProductVariant variant) {
        if (Product.findById(productId) == null) return Response.status(Response.Status.NOT_FOUND).build();
        if (variant == null || variant.sku == null || variant.sku.isBlank() || variant.stock == null || variant.stock < 0) {
            throw new BadRequestException("Phiên bản cần SKU và tồn kho hợp lệ");
        }
        variant.id = null; variant.productId = productId; if (variant.priceAdjustment == null) variant.priceAdjustment = 0L;
        variant.persist();
        syncProductStock(productId);
        return Response.status(Response.Status.CREATED).entity(variant).build();
    }

    @PUT
    @Path("/{variantId}")
    @RolesAllowed({"ADMIN", "STAFF"})
    @Transactional
    public ProductVariant update(@PathParam("productId") Long productId,
                                 @PathParam("variantId") Long variantId,
                                 ProductVariant data) {
        ProductVariant v = ProductVariant.findById(variantId);
        if (v == null || !productId.equals(v.productId)) throw new NotFoundException("Không tìm thấy phiên bản");
        if (data.sku != null && !data.sku.isBlank()) v.sku = data.sku.trim();
        if (data.color != null) v.color = data.color.trim();
        if (data.ram != null) v.ram = data.ram.trim();
        if (data.storage != null) v.storage = data.storage.trim();
        if (data.stock != null && data.stock >= 0) v.stock = data.stock;
        if (data.priceAdjustment != null) v.priceAdjustment = data.priceAdjustment;
        syncProductStock(productId);
        return v;
    }

    @DELETE
    @Path("/{variantId}")
    @RolesAllowed("ADMIN")
    @Transactional
    public Response delete(@PathParam("productId") Long productId,
                           @PathParam("variantId") Long variantId) {
        ProductVariant v = ProductVariant.findById(variantId);
        if (v == null || !productId.equals(v.productId)) throw new NotFoundException("Không tìm thấy phiên bản");
        v.delete();
        syncProductStock(productId);
        return Response.noContent().build();
    }

    /** Keep the product summary fields equal to the variant data managed by staff. */
    private void syncProductStock(Long productId) {
        Product product = Product.findById(productId);
        if (product != null) {
            List<ProductVariant> variants = ProductVariant.findByProduct(productId);
            product.stock = variants.stream()
                    .mapToLong(variant -> variant.stock == null ? 0L : variant.stock)
                    .sum();

            Set<String> colors = new LinkedHashSet<>();
            for (ProductVariant variant : variants) {
                if (variant.color != null && !variant.color.isBlank()) {
                    colors.add(variant.color.trim());
                }
            }
            product.availableColors = colors.isEmpty() ? null : String.join(", ", colors);
        }
    }
}

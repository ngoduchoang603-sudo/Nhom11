package com.shop.product.service;

import com.shop.product.dto.ProductSearchRequest;
import com.shop.product.entity.Comment;
import com.shop.product.entity.Product;
import com.shop.product.entity.ProductVariant;
import io.quarkus.panache.common.Sort;
import io.quarkus.panache.common.Page;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.transaction.Transactional;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ProductService — thao tac CRUD voi PostgreSQL qua Hibernate Panache.
 * <p>
 * Khong can @Inject Repository vi PanacheEntity da co san CRUD active-record.
 */
@ApplicationScoped
public class ProductService {

    /** Dem so san pham active */
    public long count() {
        return Product.count("status", "1");
    }

    /** Lay tat ca san pham active */
    public List<Product> findAll() {
        return Product.findActive();
    }

    /** Page catalog queries so a large table is never loaded into memory at once. */
    public List<Product> findPage(int page, int size) {
        return Product.<Product>find("status", Sort.descending("createDate"), "1")
                .page(Page.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .list();
    }

    /** Lay theo id PostgreSQL. */
    public Product findById(String id) {
        Product p;
        try {
            p = Product.findById(Long.valueOf(id));
        } catch (NumberFormatException ex) {
            throw new NotFoundException("San pham khong ton tai: " + id);
        }
        if (p == null) {
            throw new NotFoundException("San pham khong ton tai: " + id);
        }
        return p;
    }

    /** Top 8 san pham moi nhat */
    @CacheResult(cacheName = "catalog-newest")
    public List<Product> getNewest() {
        return Product.<Product>find("status", Sort.descending("createDate"), "1")
                .page(0, 8).list();
    }

    /** Top 8 san pham co discount cao nhat */
    @CacheResult(cacheName = "catalog-sale")
    public List<Product> getOnSale() {
        // Filter in Java so the result is portable across Hibernate/PostgreSQL.
        return Product.findActive().stream()
                .filter(product -> product.discount != null && product.discount > 0)
                .sorted(Comparator.comparing((Product product) -> product.discount).reversed())
                .limit(8)
                .toList();
    }

    /** Tim kiem co loc */
    public List<Product> search(ProductSearchRequest req) {
        // Truy van bat dau bang san pham active.
        // Goi .stream() truc tiep tren List<Product> de tranh van de generic
        // cua Hibernate Panache.
        var stream = Product.findActive().stream();

        if (req.keyword != null && !req.keyword.isBlank()) {
            String kw = req.keyword.toLowerCase();
            stream = stream.filter(p ->
                    (p.name != null && p.name.toLowerCase().contains(kw))
                            || (p.description != null && p.description.toLowerCase().contains(kw)));
        }
        if (req.category != null) {
            stream = stream.filter(p -> req.category.equals(p.category));
        }
        if (req.minPrice != null) {
            stream = stream.filter(p -> p.price != null && p.price >= req.minPrice);
        }
        if (req.maxPrice != null) {
            stream = stream.filter(p -> p.price != null && p.price <= req.maxPrice);
        }

        // Sap xep
        if (req.sortBy != null) {
            stream = switch (req.sortBy) {
                case "NEW" -> stream.sorted(Comparator.<Product, Instant>comparing(
                        p -> p.createDate,
                        Comparator.nullsLast(Comparator.reverseOrder())));
                case "SALE" -> stream.sorted(Comparator.<Product, Long>comparing(
                        p -> p.discount,
                        Comparator.nullsLast(Comparator.reverseOrder())));
                case "ORDER_PRICE" -> stream.sorted(Comparator.<Product, Long>comparing(
                        p -> p.price));
                case "DECS_PRICE" -> stream.sorted(Comparator.<Product, Long>comparing(
                        (Product p) -> p.price).reversed());
                default -> stream;
            };
        }
        return stream.toList();
    }

    /** Tao san pham moi */
    @Transactional
    @CacheInvalidateAll(cacheName = "catalog-newest")
    @CacheInvalidateAll(cacheName = "catalog-sale")
    public Product create(Product newProduct) {
        // Kiem tra trung ten
        long existing = Product.count("name = ?1 and status = ?2", newProduct.name, "1");
        if (existing > 0) {
            throw new WebApplicationException(
                    "San pham da ton tai tren he thong",
                    Response.Status.CONFLICT);
        }

        newProduct.status = "1";
        newProduct.createDate = Instant.now();
        if (newProduct.comments == null) {
            newProduct.comments = new ArrayList<>();
        }
        newProduct.persist();
        return newProduct;
    }

    /** Cap nhat san pham */
    @Transactional
    @CacheInvalidateAll(cacheName = "catalog-newest")
    @CacheInvalidateAll(cacheName = "catalog-sale")
    public Product update(String id, Product updateData) {
        Product existing = findById(id);

        // Defensive fallback for a database upgraded from before the @Version field.
        if (existing.version == null) existing.version = 0L;

        if (updateData.name != null && !updateData.name.isBlank()) {
            existing.name = updateData.name;
        }
        if (updateData.category != null) existing.category = updateData.category;
        if (updateData.price != null) existing.price = updateData.price;
        if (updateData.discount != null) existing.discount = updateData.discount;
        if (updateData.stock != null && updateData.stock >= 0) {
            // If product has variants, recalculate total stock from variants
            List<ProductVariant> variants = ProductVariant.findByProduct(existing.id);
            if (variants != null && !variants.isEmpty()) {
                existing.stock = variants.stream().mapToLong(v -> v.stock != null ? v.stock : 0L).sum();
            } else {
                existing.stock = updateData.stock;
            }
        }
        if (updateData.description != null) existing.description = updateData.description;
        if (updateData.battery != null) existing.battery = updateData.battery;
        if (updateData.screen != null) existing.screen = updateData.screen;
        if (updateData.storage != null) existing.storage = updateData.storage;
        if (updateData.ram != null) existing.ram = updateData.ram;
        if (updateData.image != null) existing.image = updateData.image;

        return existing;
    }

    /** Atomically reserves the selected SKU and keeps the catalog total in sync. */
    @Transactional
    public void decreaseVariantStock(String productId, Long variantId, long quantity) {
        if (variantId == null || quantity < 1) throw new WebApplicationException("Phiên bản hoặc số lượng không hợp lệ", Response.Status.BAD_REQUEST);
        Product product;
        try { product = Product.findById(Long.valueOf(productId), LockModeType.PESSIMISTIC_WRITE); }
        catch (NumberFormatException ex) { throw new NotFoundException("Không tìm thấy sản phẩm: " + productId); }
        if (product == null) throw new NotFoundException("Không tìm thấy sản phẩm: " + productId);
        if (!"1".equals(product.status)) throw new WebApplicationException("Sản phẩm đã ngừng bán", Response.Status.GONE);
        ProductVariant variant = ProductVariant.findById(variantId, LockModeType.PESSIMISTIC_WRITE);
        if (variant == null || !product.id.equals(variant.productId)) throw new NotFoundException("Không tìm thấy phiên bản sản phẩm");
        if (variant.stock == null || variant.stock < quantity || product.stock == null || product.stock < quantity) {
            throw new WebApplicationException(
                    String.format("Không đủ hàng: yêu cầu %d nhưng phiên bản còn %d, sản phẩm còn %d",
                            quantity, variant.stock != null ? variant.stock : 0, product.stock != null ? product.stock : 0),
                    Response.Status.CONFLICT);
        }
        variant.stock -= quantity;
        product.stock -= quantity;
    }

    /** Xoa mem */
    @Transactional
    @CacheInvalidateAll(cacheName = "catalog-newest")
    @CacheInvalidateAll(cacheName = "catalog-sale")
    public void delete(String id) {
        Product p = findById(id);
        p.status = "0";
    }

    /** Them comment vao san pham. */
    @Transactional
    public Product addComment(String productId, Long userId, String username, String message, Integer rating) {
        Product p = findById(productId);
        if (p.comments == null) {
            p.comments = new ArrayList<>();
        }
        Comment c = p.comments.stream().filter(existing -> userId.equals(existing.userId)).findFirst().orElse(null);
        if (c == null) {
            c = new Comment();
            c.userId = userId;
            p.comments.add(c);
        }
        c.username = username;
        c.message = message.trim();
        c.rating = rating;
        c.createDate = Instant.now();
        return p;
    }

    /** Giam stock khi co don hang */
    @Transactional
    public void decreaseStock(String productId, long quantity) {
        if (quantity < 1) {
            throw new WebApplicationException("Số lượng phải lớn hơn 0", Response.Status.BAD_REQUEST);
        }
        Product p;
        try { p = Product.findById(Long.valueOf(productId), LockModeType.PESSIMISTIC_WRITE); }
        catch (NumberFormatException ex) { throw new NotFoundException("San pham khong ton tai: " + productId); }
        if (p == null || !"1".equals(p.status)) throw new NotFoundException("San pham khong ton tai: " + productId);
        if (p.version == null) p.version = 0L;
        if (p.stock < quantity) {
            throw new WebApplicationException(
                    String.format("Không đủ hàng: yêu cầu %d nhưng chỉ còn %d", quantity, p.stock),
                    Response.Status.CONFLICT);
        }
        p.stock -= quantity;
    }
}

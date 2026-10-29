package com.shop.product.dto;

/**
 * DTO cho yeu cau tim kiem san pham co loc.
 * Mapping voi NewSearch trong project Spring goc.
 */
public class ProductSearchRequest {
    public String keyword;
    public String category;
    public Long minPrice;
    public Long maxPrice;
    /** Sort: NEW, SALE, ORDER_PRICE (asc), DECS_PRICE (desc) */
    public String sortBy;
}

package com.shop.product.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.Version;
import jakarta.persistence.JoinColumn;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/**
 * Product entity — luu trong PostgreSQL qua Hibernate ORM/JPA Panache.
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_status", columnList = "status"),
        @Index(name = "idx_products_status_category", columnList = "status, category"),
        @Index(name = "idx_products_created", columnList = "createDate")
})
public class Product extends PanacheEntity {

    /** Optimistic concurrency guard for stock changes during checkout. */
    @Version public Long version;

    public String status; // "1" = active, "0" = deleted

    @NotBlank(message = "Ten san pham khong duoc trong")
    public String name;

    @Min(value = 0, message = "Gia khong duoc am")
    public Long price;

    public Long discount;

    /** Thuong hieu: SamSung, Apple, Xiaomi, Vivo, Oppo, Realme */
    public String category;

    public String image;

    @Min(value = 0, message = "So luong khong duoc am")
    public Long stock;

    public String description;
    public String screen;
    public String storage;
    public String ram;
    public String battery;
    /** Danh sach mau dang ban, phan cach boi dau phay. Ton kho chi tiet theo mau se duoc mo rong sau. */
    public String availableColors;

    public Instant createDate;

    /** Binh luan duoc luu trong bang product_comments cua PostgreSQL. */
    @ElementCollection
    @CollectionTable(name = "product_comments", joinColumns = @JoinColumn(name = "product_id"))
    public List<Comment> comments;

    // -------- Phuong thuc query --------

    public static List<Product> findActive() {
        return list("status", "1");
    }

    public static List<Product> findByCategory(String category) {
        return list("status = ?1 and category = ?2", "1", category);
    }

    public static List<Product> searchByName(String keyword) {
        return list("status = ?1 and lower(name) like ?2", "1", "%" + keyword.toLowerCase() + "%");
    }
}

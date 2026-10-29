package com.shop.product.entity;

import jakarta.persistence.Embeddable;
import java.time.Instant;

/**
 * Comment — embedded vao Product entity.
 * <p>
 * Day la diem hay cua JPA @Embeddable: thay vi tach Comment ra mot table rieng
 * va dung khoa ngoai, ta nhung truc tiep mang Comment vao Product
 * → mot lan truy van la lay ca product + comments.
 * <p>
 * Trade-off: chi nen embed khi:
 *   1. So luong comment khong qua lon.
 *   2. Luon doc cung voi Product.
 *   3. Khong can query Comment doc lap.
 */
@Embeddable
public class Comment {
    public Long userId;        // tham chieu sang user-service (microservice khac)
    public String username;    // cache lai de hien thi nhanh (denormalization)
    public String message;
    /** Optional 1-5 star score; plain comments from older data remain valid. */
    public Integer rating;
    public Instant createDate;
}

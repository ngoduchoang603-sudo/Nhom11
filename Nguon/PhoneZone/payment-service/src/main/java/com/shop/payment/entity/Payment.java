package com.shop.payment.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

/**
 * Payment — luu mot lan thanh toan.
 * <p>
 * Mot Order co the co nhieu Payment neu lan dau fail roi user thu lai.
 * Vi the dung @Column thay vi @OneToOne.
 */
@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_request_id", columnList = "request_id", unique = true),
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_status", columnList = "status")
})
public class Payment extends PanacheEntity {

    /**
     * requestId: ma duy nhat moi lan tao thanh toan, gui sang MoMo.
     * Dung lam khoa de tranh trung khi MoMo gui IPN nhieu lan.
     */
    @Column(name = "request_id", nullable = false, unique = true, length = 100)
    public String requestId;

    /** orderId gui sang MoMo (khac voi id cua bang order ben order-service) */
    @Column(name = "momo_order_id", nullable = false, length = 100)
    public String momoOrderId;

    /** Id cua Order ben order-service */
    @Column(name = "order_id", nullable = false)
    public Long orderId;

    /** Id cua user */
    @Column(name = "user_id", nullable = false)
    public Long userId;

    /** So tien (VND) */
    @Column(nullable = false)
    public Long amount;

    /** Thong tin don hang (hien thi trong app MoMo) */
    @Column(name = "order_info", length = 500)
    public String orderInfo;

    /** Phuong thuc thanh toan: MOMO, ZALOPAY, VNPAY, ... */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public PaymentMethod method = PaymentMethod.MOMO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public PaymentStatus status = PaymentStatus.PENDING;

    /** URL/QR code de user thanh toan (lay tu phan hoi cua MoMo) */
    @Column(name = "pay_url", length = 1000)
    public String payUrl;

    @Column(name = "qr_code_url", length = 1000)
    public String qrCodeUrl;

    @Column(name = "deeplink", length = 1000)
    public String deeplink;

    /** ID giao dich cua MoMo (lay khi giao dich thanh cong) */
    @Column(name = "transaction_id", length = 100)
    public String transactionId;

    /** Code ket qua cua MoMo (0 = thanh cong) */
    @Column(name = "result_code")
    public Integer resultCode;

    /** Thong bao tu MoMo */
    @Column(name = "message", length = 500)
    public String message;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // -------- Query methods --------

    public static Payment findByRequestId(String requestId) {
        return find("requestId", requestId).firstResult();
    }

    public static Payment findByMomoOrderId(String momoOrderId) {
        return find("momoOrderId", momoOrderId).firstResult();
    }

    public static List<Payment> findByOrderId(Long orderId) {
        return list("orderId", orderId);
    }

    public static List<Payment> findPending() {
        return list("status", PaymentStatus.PENDING);
    }
}

package com.shop.payment.entity;

public enum PaymentStatus {
    /** Vua tao, dang cho user quet QR / chuyen tien */
    PENDING,
    /** Thanh toan thanh cong */
    SUCCESS,
    /** Thanh toan that bai (sai mat khau, het tien, huy, ...) */
    FAILED,
    /** Het han (qua thoi gian cho) */
    EXPIRED,
    /** Da hoan tien */
    REFUNDED
}

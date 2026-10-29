package com.shop.order.entity;

/** Cac trang thai cua mot don hang */
public enum OrderStatus {
    PENDING,     // Cho xac nhan
    CONFIRMED,   // Da xac nhan
    PACKING,     // Dang dong goi
    READY_TO_SHIP, // Da ban giao don vi van chuyen
    SHIPPING,    // Dang giao
    OUT_FOR_DELIVERY, // Shipper dang giao den khach
    DELIVERED,   // Da giao
    CANCELLED    // Da huy
}

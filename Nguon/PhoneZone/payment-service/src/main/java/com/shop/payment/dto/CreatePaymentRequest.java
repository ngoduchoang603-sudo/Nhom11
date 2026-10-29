package com.shop.payment.dto;

import com.shop.payment.entity.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request tu frontend khi user bam "Thanh toan".
 */
public class CreatePaymentRequest {

    @NotNull(message = "orderId la bat buoc")
    public Long orderId;

    @NotNull
    public Long userId;

    @NotNull
    @Min(value = 1000, message = "So tien thap nhat la 1000 VND")
    public Long amount;

    public String orderInfo;

    public PaymentMethod method = PaymentMethod.MOMO;
}

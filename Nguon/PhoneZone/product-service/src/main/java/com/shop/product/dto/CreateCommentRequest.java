package com.shop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Noi dung binh luan do khach hang gui. Danh tinh lay tu JWT, khong lay tu browser. */
public class CreateCommentRequest {
    @NotBlank(message = "Noi dung binh luan khong duoc de trong")
    @Size(max = 1000, message = "Binh luan toi da 1000 ky tu")
    public String message;

    @NotNull(message = "Vui long chon so sao")
    @Min(value = 1, message = "Danh gia phai tu 1 den 5 sao")
    @Max(value = 5, message = "Danh gia phai tu 1 den 5 sao")
    public Integer rating;
}

package com.shop.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO chua thong tin dang nhap (username + password).
 * Dung Java record cho gon — Quarkus / Jackson ho tro san.
 */
public record LoginRequest(
        @NotBlank(message = "Username khong duoc de trong")
        String username,

        @NotBlank(message = "Password khong duoc de trong")
        String password
) {}

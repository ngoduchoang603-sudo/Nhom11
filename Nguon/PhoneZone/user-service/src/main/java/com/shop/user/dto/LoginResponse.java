package com.shop.user.dto;

/** Response sau khi dang nhap thanh cong, chua JWT token */
public record LoginResponse(
        String token,
        String username,
        String role
) {}

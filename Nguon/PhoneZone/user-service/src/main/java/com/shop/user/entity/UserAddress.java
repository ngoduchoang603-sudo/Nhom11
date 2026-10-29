package com.shop.user.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "user_addresses", indexes = {
        @Index(name = "idx_user_addresses_user", columnList = "user_id")
})
public class UserAddress extends PanacheEntity {
    @Column(name = "user_id", nullable = false)
    public Long userId;

    @NotBlank @Size(max = 100)
    @Column(name = "recipient_name", nullable = false, length = 100)
    public String recipientName;

    @NotBlank @Size(max = 25)
    @Column(nullable = false, length = 25)
    public String phone;

    @NotBlank @Size(max = 500)
    @Column(nullable = false, length = 500)
    public String address;

    @Column(name = "address_label", length = 40)
    public String label;

    @Column(name = "is_default", nullable = false)
    public boolean defaultAddress;
}

package com.shop.user.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entity User — anh xa toi bang "users" trong PostgreSQL.
 * <p>
 * Su dung Panache (PanacheEntity) — Quarkus tu sinh san field "id" kieu Long
 * va cac phuong thuc tinh nhu findById, listAll, persist...
 * Day la mot trong nhung diem khac biet lon voi Spring Data JPA:
 * khong can viet Repository interface ma van co san thao tac CRUD.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_status", columnList = "status"),
        @Index(name = "idx_users_email", columnList = "email")
})
public class User extends PanacheEntity {

    // Trang thai: "1" = active, "0" = deleted (giu nguyen logic tu project Spring goc)
    public String status;

    @NotBlank(message = "Username khong duoc trong")
    @Size(min = 3, max = 50, message = "Username phai tu 3-50 ky tu")
    @Column(unique = true)
    public String username;

    /**
     * Password — chi cho phep deserialize (nhan tu request) khong cho serialize ra response.
     * Day la cach an toan de tranh lo password khi tra User ra REST API.
     */
    @NotBlank(message = "Password khong duoc trong")
    @Size(min = 6, message = "Password phai tu 6 ky tu tro len")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String password;

    @Column(name = "full_name")
    public String fullName;

    @Email(message = "Email khong hop le")
    public String email;

    public String phone;
    public String city;
    public String district;
    public String address;
    public String image;

    @Enumerated(EnumType.STRING)
    public GenderEnum gender;

    @Enumerated(EnumType.STRING)
    public RoleEnum role = RoleEnum.USER;

    // -------- Cac phuong thuc query tien ich (Panache active-record) --------

    /** Tim user theo username, chi lay user active */
    public static User findByUsername(String username) {
        return find("username = ?1 and status = ?2", username, "1").firstResult();
    }

    /** Used during registration: even a soft-deleted account still owns its username. */
    public static boolean usernameExists(String username) {
        return username != null && count("username", username.trim()) > 0;
    }

    /** Kiem tra email da ton tai chua */
    public static boolean emailExists(String email) {
        return count("email", email) > 0;
    }
}

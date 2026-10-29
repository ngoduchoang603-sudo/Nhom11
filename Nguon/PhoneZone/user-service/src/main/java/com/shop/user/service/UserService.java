package com.shop.user.service;

import com.shop.user.dto.LoginRequest;
import com.shop.user.dto.LoginResponse;
import com.shop.user.entity.RoleEnum;
import com.shop.user.entity.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * UserService — xu ly logic nghiep vu cho User.
 * <p>
 * Khac biet so voi Spring:
 * - Dung @ApplicationScoped (CDI) thay vi @Service.
 * - Dung @Transactional (jakarta.transaction) thay vi cua Spring.
 * - Khong can @Autowired Repository — Panache cho phep goi truc tiep User.findById(),
 *   User.persist(), etc.
 */
@ApplicationScoped
public class UserService {

    /** Admin needs both active and locked accounts to manage their status. */
    public List<User> getAllUsers() {
        return User.listAll();
    }

    public List<User> getPage(int page, int size) {
        return User.findAll(Sort.ascending("id"))
                .page(Page.of(Math.max(0, page), Math.min(100, Math.max(1, size)))).list();
    }

    /** Lay user theo id */
    public User getUserById(Long id) {
        User user = User.findById(id);
        if (user == null) {
            throw new NotFoundException("User khong ton tai voi id=" + id);
        }
        return user;
    }

    /** Dang ky user moi */
    @Transactional
    public User register(User newUser, boolean adminCreated) {
        if (User.usernameExists(newUser.username)) {
            throw new WebApplicationException(
                    "Username da ton tai", Response.Status.CONFLICT);
        }
        if (User.emailExists(newUser.email)) {
            throw new WebApplicationException(
                    "Email da ton tai", Response.Status.CONFLICT);
        }

        // Hash password truoc khi luu (BCrypt — chuan an toan)
        newUser.password = BcryptUtil.bcryptHash(newUser.password);
        newUser.status = "1";
        if (!adminCreated || newUser.role == null) newUser.role = RoleEnum.USER;
        newUser.persist();
        return newUser;
    }

    /** Cap nhat user */
    @Transactional
    public User updateUser(Long id, User updateData, boolean admin) {
        User existing = getUserById(id);

        if (updateData.fullName != null) existing.fullName = updateData.fullName;
        if (updateData.email != null && !updateData.email.equalsIgnoreCase(existing.email)) {
            if (User.emailExists(updateData.email)) {
                throw new WebApplicationException("Email da ton tai", Response.Status.CONFLICT);
            }
            existing.email = updateData.email;
        }
        if (updateData.phone != null) existing.phone = updateData.phone;
        if (updateData.city != null) existing.city = updateData.city;
        if (updateData.district != null) existing.district = updateData.district;
        if (updateData.address != null) existing.address = updateData.address;
        if (updateData.gender != null) existing.gender = updateData.gender;
        // Role, status and password are administrative controls. A normal user
        // may update only their own profile fields through the same endpoint.
        if (admin) {
            if (updateData.role != null) existing.role = updateData.role;
            if ("0".equals(updateData.status) || "1".equals(updateData.status)) existing.status = updateData.status;
            if (updateData.password != null && !updateData.password.isBlank()) {
                if (updateData.password.length() < 6) {
                    throw new WebApplicationException("Mat khau phai tu 6 ky tu", Response.Status.BAD_REQUEST);
                }
                existing.password = BcryptUtil.bcryptHash(updateData.password);
            }
        }
        // Panache tu dong flush khi ket thuc transaction
        return existing;
    }

    /** Xoa mem — chuyen status = "0" thay vi xoa han */
    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);
        user.status = "0";
    }

    /** Dang nhap — kiem tra mat khau va sinh JWT token */
    public LoginResponse login(LoginRequest req) {
        User user = User.findByUsername(req.username());
        if (user == null) {
            throw new WebApplicationException(
                    "Sai tai khoan hoac mat khau",
                    Response.Status.UNAUTHORIZED);
        }
        if (!BcryptUtil.matches(req.password(), user.password)) {
            throw new WebApplicationException(
                    "Sai tai khoan hoac mat khau",
                    Response.Status.UNAUTHORIZED);
        }

        // Sinh JWT token co thoi han 1 gio
        String token = Jwt.issuer("https://shop.example.com")
                // Subject is a standard JWT claim and is reliably available to every microservice.
                .subject(String.valueOf(user.id))
                .upn(user.username)
                .groups(Set.of(user.role.name()))
                .claim("userId", user.id)
                .expiresIn(Duration.ofHours(1))
                .sign();

        return new LoginResponse(token, user.username, user.role.name());
    }
}

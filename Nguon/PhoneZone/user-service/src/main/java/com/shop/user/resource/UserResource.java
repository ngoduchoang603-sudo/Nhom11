package com.shop.user.resource;

import com.shop.user.dto.LoginRequest;
import com.shop.user.dto.LoginResponse;
import com.shop.user.entity.User;
import com.shop.user.service.UserService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * UserResource — REST endpoints CRUD cho User.
 * <p>
 * Anh xa Spring -> Quarkus:
 *   @RestController + @RequestMapping("/users")  ->  @Path("/api/users")
 *   @GetMapping  -> @GET
 *   @PostMapping -> @POST
 *   @PathVariable -> @PathParam
 *   @RequestBody  -> tham so (tu nhan dien)
 *   @PreAuthorize("hasRole('ADMIN')") -> @RolesAllowed("ADMIN")
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "User", description = "Quan ly nguoi dung")
public class UserResource {

    @Inject
    UserService userService;

    @Inject
    SecurityIdentity identity;

    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "Lay danh sach tat ca user (chi admin)")
    public List<User> getAll() {
        return userService.getAllUsers();
    }

    @GET
    @Path("/page")
    @RolesAllowed("ADMIN")
    public List<User> getPage(@QueryParam("page") @DefaultValue("0") int page,
                              @QueryParam("size") @DefaultValue("50") int size) {
        return userService.getPage(page, size);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "USER"})
    @Operation(summary = "Lay user theo id")
    public User getById(@PathParam("id") Long id) {
        if (!identity.hasRole("ADMIN") && !id.equals(authenticatedUserId())) {
            throw new ForbiddenException("Khong the xem tai khoan cua nguoi dung khac");
        }
        return userService.getUserById(id);
    }

    @POST
    @Path("/register")
    @PermitAll
    @Operation(summary = "Dang ky tai khoan moi")
    public Response register(@Valid User user) {
        User created = userService.register(user, identity.hasRole("ADMIN"));
        // Password se khong duoc serialize ra response nho @JsonProperty WRITE_ONLY
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "Dang nhap, tra ve JWT token")
    public LoginResponse login(@Valid LoginRequest request) {
        return userService.login(request);
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "USER"})
    @Operation(summary = "Cap nhat thong tin user")
    public User update(@PathParam("id") Long id, User user) {
        boolean admin = identity.hasRole("ADMIN");
        if (!admin && !id.equals(authenticatedUserId())) {
            throw new ForbiddenException("Khong the cap nhat tai khoan cua nguoi dung khac");
        }
        return userService.updateUser(id, user, admin);
    }

    private Long authenticatedUserId() {
        if (identity.getPrincipal() instanceof JsonWebToken token) {
            Object claim = token.getClaim("userId");
            if (claim instanceof Number number) return number.longValue();
            try { return claim == null ? null : Long.valueOf(String.valueOf(claim)); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Xoa user (soft delete)")
    public Response delete(@PathParam("id") Long id) {
        userService.deleteUser(id);
        return Response.noContent().build();
    }
}

package com.shop.user.resource;

import com.shop.user.entity.UserAddress;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/users/{userId}/addresses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
public class AddressResource {
    @GET
    public List<UserAddress> list(@PathParam("userId") Long userId) {
        return UserAddress.list("userId = ?1 order by defaultAddress desc, id desc", userId);
    }

    @POST
    @Transactional
    public Response create(@PathParam("userId") Long userId, UserAddress input) {
        validate(input);
        boolean first = UserAddress.count("userId", userId) == 0;
        if (input.defaultAddress || first) UserAddress.update("defaultAddress = false where userId = ?1", userId);
        input.id = null; input.userId = userId; input.defaultAddress = input.defaultAddress || first; input.persist();
        return Response.status(Response.Status.CREATED).entity(input).build();
    }

    @PUT @Path("/{id}")
    @Transactional
    public UserAddress update(@PathParam("userId") Long userId, @PathParam("id") Long id, UserAddress input) {
        UserAddress existing = owned(userId, id); validate(input);
        existing.recipientName = input.recipientName.trim(); existing.phone = input.phone.trim(); existing.address = input.address.trim(); existing.label = trim(input.label);
        if (input.defaultAddress) { UserAddress.update("defaultAddress = false where userId = ?1", userId); existing.defaultAddress = true; }
        return existing;
    }

    @PATCH @Path("/{id}/default")
    @Transactional
    public UserAddress setDefault(@PathParam("userId") Long userId, @PathParam("id") Long id) {
        UserAddress existing = owned(userId, id); UserAddress.update("defaultAddress = false where userId = ?1", userId); existing.defaultAddress = true; return existing;
    }

    @DELETE @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("userId") Long userId, @PathParam("id") Long id) { owned(userId, id).delete(); return Response.noContent().build(); }

    private UserAddress owned(Long userId, Long id) { UserAddress a = UserAddress.findById(id); if (a == null || !userId.equals(a.userId)) throw new NotFoundException("Không tìm thấy địa chỉ"); return a; }
    private void validate(UserAddress a) { if (a == null || blank(a.recipientName) || blank(a.phone) || blank(a.address)) throw new BadRequestException("Cần nhập tên người nhận, số điện thoại và địa chỉ"); }
    private boolean blank(String v) { return v == null || v.isBlank(); }
    private String trim(String v) { return v == null ? null : v.trim(); }
}

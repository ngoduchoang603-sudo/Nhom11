package com.shop.order.resource;

import com.shop.order.entity.Coupon;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;

@Path("/api/coupons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CouponResource {
    @GET @RolesAllowed("ADMIN") public List<Coupon> all() { return Coupon.listAll(); }

    /** Preview only: lets the customer see the actual reduction before creating an order. */
    @GET @Path("/preview") @PermitAll
    public CouponPreview preview(@QueryParam("code") String code, @QueryParam("subtotal") long subtotal) {
        if (code == null || code.isBlank() || subtotal < 0) throw new BadRequestException("Vui lòng nhập mã giảm giá hợp lệ");
        Coupon coupon = Coupon.findByCode(code.trim());
        long discount = calculate(coupon, subtotal);
        if (discount < 0) throw new BadRequestException("Mã giảm giá không hợp lệ, hết hạn hoặc chưa đủ điều kiện");
        return new CouponPreview(coupon.code, coupon.name, discount, Math.max(0L, subtotal - discount));
    }

    @POST @RolesAllowed("ADMIN") @Transactional
    public Response create(Coupon coupon) {
        if (coupon.code == null || coupon.code.isBlank() || coupon.value == null || coupon.value < 0) throw new BadRequestException("Mã và giá trị giảm là bắt buộc");
        coupon.code = coupon.code.trim().toUpperCase();
        if (Coupon.findByCode(coupon.code) != null) throw new ClientErrorException("Mã giảm giá đã tồn tại", 409);
        coupon.type = "FIXED".equalsIgnoreCase(coupon.type) ? "FIXED" : "PERCENT";
        if ("PERCENT".equals(coupon.type) && coupon.value > 100) throw new BadRequestException("Phần trăm giảm không được vượt 100");
        coupon.active = coupon.active == null || coupon.active;
        coupon.usedCount = 0L;
        coupon.persist();
        return Response.status(201).entity(coupon).build();
    }

    @PUT @Path("/{id}") @RolesAllowed("ADMIN") @Transactional
    public Coupon update(@PathParam("id") Long id, Coupon input) {
        Coupon c = Coupon.findById(id); if (c == null) throw new NotFoundException("Không tìm thấy mã giảm giá");
        if (input.name != null) c.name = input.name;
        if (input.value != null && input.value >= 0) c.value = input.value;
        if (input.minOrderValue != null && input.minOrderValue >= 0) c.minOrderValue = input.minOrderValue;
        if (input.type != null) c.type = "FIXED".equalsIgnoreCase(input.type) ? "FIXED" : "PERCENT";
        if (input.startAt != null) c.startAt = input.startAt;
        if (input.endAt != null) c.endAt = input.endAt;
        if (input.active != null) c.active = input.active;
        if (c.endAt != null && c.startAt != null && c.endAt.isBefore(c.startAt)) throw new BadRequestException("Thời gian kết thúc phải sau thời gian bắt đầu");
        return c;
    }

    @DELETE @Path("/{id}") @RolesAllowed("ADMIN") @Transactional
    public Response disable(@PathParam("id") Long id) { Coupon c=Coupon.findById(id); if(c==null) throw new NotFoundException("Không tìm thấy mã giảm giá"); c.active=false; return Response.noContent().build(); }

    public static long calculate(Coupon c, long subtotal) {
        if (c == null || !Boolean.TRUE.equals(c.active) || (c.startAt != null && Instant.now().isBefore(c.startAt)) || (c.endAt != null && Instant.now().isAfter(c.endAt)) || (c.usageLimit != null && c.usedCount >= c.usageLimit) || subtotal < (c.minOrderValue == null ? 0 : c.minOrderValue)) return -1;
        long discount = "PERCENT".equals(c.type) ? subtotal * c.value / 100 : c.value;
        return Math.min(discount, subtotal);
    }

    public record CouponPreview(String code, String name, long discount, long subtotalAfterDiscount) { }
}

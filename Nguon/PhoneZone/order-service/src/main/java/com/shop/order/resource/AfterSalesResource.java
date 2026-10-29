package com.shop.order.resource;

import com.shop.order.entity.AfterSalesCase;
import com.shop.order.entity.AfterSalesStatus;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Path("/api/after-sales")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AfterSalesResource {
    @GET @RolesAllowed("ADMIN") public List<AfterSalesCase> all() { return AfterSalesCase.list("order by createdAt desc"); }
    @GET @Path("/user/{userId}") @RolesAllowed({"USER","ADMIN"}) public List<AfterSalesCase> byUser(@PathParam("userId") Long id) { return AfterSalesCase.list("userId = ?1 order by createdAt desc", id); }
    @POST @RolesAllowed({"USER","ADMIN"}) @Transactional
    public Response create(AfterSalesCase c) {
        if (c.orderId == null || c.userId == null || c.imei == null || c.imei.isBlank() || !("RETURN".equals(c.requestType) || "WARRANTY".equals(c.requestType))) throw new BadRequestException("Cần có đơn gốc, IMEI/serial và loại yêu cầu");
        Order order = Order.findById(c.orderId);
        if (order == null || !c.userId.equals(order.userId)) throw new NotFoundException("Không tìm thấy đơn hàng của khách");
        if (order.status != OrderStatus.DELIVERED) throw new BadRequestException("Chỉ có thể tạo yêu cầu sau khi đơn đã giao thành công");
        if (c.productId == null || order.items == null || order.items.stream().noneMatch(item -> c.productId.equals(item.productId))) throw new BadRequestException("Sản phẩm không thuộc đơn hàng gốc");
        long active = AfterSalesCase.count("imei = ?1 and status not in ?2", c.imei.trim(), List.of(AfterSalesStatus.REJECTED, AfterSalesStatus.CLOSED, AfterSalesStatus.RESOLVED_REFUND, AfterSalesStatus.RESOLVED_REPLACEMENT, AfterSalesStatus.RESOLVED_REPAIRED));
        if (active > 0) throw new ClientErrorException("IMEI/serial này đã có yêu cầu hậu mãi đang xử lý", 409);
        Instant deliveredAt = order.date == null ? Instant.now() : order.date;
        c.imei = c.imei.trim().toUpperCase();
        c.status = AfterSalesStatus.PENDING_APPROVAL;
        c.returnDeadline = deliveredAt.plus(7, ChronoUnit.DAYS);
        c.warrantyStart = deliveredAt;
        c.warrantyEnd = deliveredAt.plus(365, ChronoUnit.DAYS);
        c.persist();
        return Response.status(201).entity(c).build();
    }
    @PATCH @Path("/{id}/status") @RolesAllowed({"ADMIN","STAFF"}) @Transactional
    public AfterSalesCase update(@PathParam("id") Long id, @QueryParam("status") AfterSalesStatus status, @QueryParam("note") String note) {
        AfterSalesCase c=AfterSalesCase.findById(id); if(c==null) throw new NotFoundException("Không tìm thấy yêu cầu hậu mãi"); if(status==null) throw new BadRequestException("Trạng thái không hợp lệ"); c.status=status; if(note!=null&&!note.isBlank()) c.staffNote=note; return c;
    }
}

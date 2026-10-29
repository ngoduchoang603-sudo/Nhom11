package com.shop.payment.resource;

import com.shop.payment.dto.CreatePaymentRequest;
import com.shop.payment.dto.MomoIpnRequest;
import com.shop.payment.entity.Payment;
import com.shop.payment.service.PaymentService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * REST API cho thanh toan.
 */
@Path("/api/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Payment", description = "Thanh toan qua MoMo va cac cong khac")
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);

    @Inject
    PaymentService paymentService;

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "frontend.base-url", defaultValue = "http://localhost:8080")
    String frontendBaseUrl;

    // -------------------------------------------------------------
    // ENDPOINT TAO THANH TOAN
    // -------------------------------------------------------------

    /**
     * Frontend goi endpoint nay sau khi user bam "Thanh toan voi MoMo".
     * <p>
     * Tra ve qrCodeUrl + payUrl + deeplink cho frontend hien thi.
     */
    @POST
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Tao giao dich thanh toan, tra ve QR code va link")
    public Response create(@Valid CreatePaymentRequest req) {
        requireCurrentUser(req.userId);
        Payment payment = paymentService.createPayment(req);
        // ZaloPay Sandbox returns order_url as the raw EMV QR payload, not a browser URL.
        // Never redirect a browser to that string: serve it through the PhoneZone QR screen instead.
        String browserPayUrl = payment.payUrl != null ? payment.payUrl : "";
        if (payment.method != null && "ZALOPAY".equals(payment.method.name()) && !browserPayUrl.startsWith("http")) {
            browserPayUrl = frontendBaseUrl + "/payment-qr.html?paymentId=" + payment.id;
        }
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "paymentId", payment.id,
                        "requestId", payment.requestId,
                        "momoOrderId", payment.momoOrderId,
                        "amount", payment.amount,
                        "method", payment.method.name(),
                        "status", payment.status.name(),
                        "message", payment.message != null ? payment.message : "",
                        "qrCodeUrl", payment.qrCodeUrl != null ? payment.qrCodeUrl : "",
                        "payUrl", browserPayUrl,
                        "deeplink", payment.deeplink != null ? payment.deeplink : ""
                ))
                .build();
    }

    // -------------------------------------------------------------
    // ENDPOINT IPN - MoMo CALLBACK
    // -------------------------------------------------------------

    /**
     * Endpoint nhan IPN tu MoMo.
     * <p>
     * QUAN TRONG:
     *   1. @PermitAll vi MoMo khong gui JWT token, ta verify bang signature.
     *   2. Theo docs MoMo: phai tra ve HTTP 204 No Content de bao da nhan.
     *   3. Ket qua xu ly (thanh cong hay khong) chi log noi bo,
     *      MoMo khong quan tam.
     */
    @POST
    @Path("/momo/ipn")
    @PermitAll
    @Operation(summary = "Webhook MoMo goi ve khi giao dich co ket qua")
    public Response handleMomoIpn(MomoIpnRequest ipn) {
        LOG.infof("== MoMo IPN: orderId=%s, requestId=%s, resultCode=%d ==",
                ipn.orderId, ipn.requestId, ipn.resultCode);
        try {
            paymentService.handleIpn(ipn);
        } catch (Exception e) {
            LOG.errorf(e, "Loi khi xu ly IPN: %s", e.getMessage());
        }
        // MoMo yeu cau tra ve 204
        return Response.noContent().build();
    }

    @POST
    @Path("/zalopay/callback")
    @PermitAll
    @Operation(summary = "ZaloPay callback khi giao dịch hoàn tất")
    public Response handleZaloPayCallback(Map<String, Object> body) {
        boolean valid = paymentService.handleZaloPayCallback(
                body.get("data") == null ? null : String.valueOf(body.get("data")),
                body.get("mac") == null ? null : String.valueOf(body.get("mac")));
        return Response.ok(Map.of("return_code", valid ? 1 : -1,
                "return_message", valid ? "success" : "invalid callback")).build();
    }

    @GET
    @Path("/vnpay/return")
    @PermitAll
    @Operation(summary = "VNPay trả kết quả thanh toán về trình duyệt khách")
    public Response handleVnpayReturn(@Context UriInfo uriInfo) {
        java.util.Map<String,String> params = new java.util.LinkedHashMap<>();
        uriInfo.getQueryParameters().forEach((key, values) -> params.put(key, values.isEmpty() ? "" : values.get(0)));
        paymentService.handleVnpayReturn(params);
        Payment payment = paymentService.findByGatewayReference(params.get("vnp_TxnRef"));
        String result = payment != null && payment.status == com.shop.payment.entity.PaymentStatus.SUCCESS ? "success" : "failed";
        String paymentId = payment == null ? "" : String.valueOf(payment.id);
        return Response.seeOther(java.net.URI.create(frontendBaseUrl + "/cart.html?gateway=VNPAY&result=" + result + "&paymentId=" + paymentId)).build();
    }

    // -------------------------------------------------------------
    // ENDPOINT POLLING / QUERY
    // -------------------------------------------------------------

    /**
     * Frontend goi sau khi user quay lai tu MoMo de check ket qua.
     * Service nay se goi sang MoMo query API de lay trang thai moi nhat.
     */
    @POST
    @Path("/{id}/query")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Hoi trang thai giao dich tu MoMo (polling)")
    public Payment query(@PathParam("id") Long id) {
        requireOwnPayment(paymentService.getById(id));
        return paymentService.queryAndSync(id);
    }

    // -------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------

    @GET
    @Path("/{id}")
    @RolesAllowed({"USER", "ADMIN"})
    public Payment getById(@PathParam("id") Long id) {
        Payment payment = paymentService.getById(id);
        requireOwnPayment(payment);
        return payment;
    }

    @GET
    @Path("/order/{orderId}")
    @RolesAllowed({"USER", "ADMIN"})
    public List<Payment> getByOrder(@PathParam("orderId") Long orderId) {
        List<Payment> payments = paymentService.getByOrderId(orderId);
        if (!identity.hasRole("ADMIN")) {
            Long authenticatedId = authenticatedUserId();
            if (authenticatedId == null || payments.stream().anyMatch(payment -> !authenticatedId.equals(payment.userId))) {
                throw new ForbiddenException("Bạn không có quyền xem thanh toán của đơn hàng này");
            }
        }
        return payments;
    }

    @GET
    @RolesAllowed("ADMIN")
    public List<Payment> getAll() {
        return paymentService.getAll();
    }

    // -------------------------------------------------------------
    // ADMIN - XAC NHAN THU CONG
    // -------------------------------------------------------------

    /**
     * Xac nhan thu cong chi danh cho quan tri vien; khach hang khong the tu
     * danh dau da thanh toan.
     */
    @POST
    @Path("/{id}/manual-confirm")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Xac nhan thanh cong/that bai (demo hoac admin)")
    public Payment manualConfirm(
            @PathParam("id") Long id,
            @QueryParam("success") boolean success,
            @QueryParam("note") String note) {
        return paymentService.manualConfirm(id, success, note);
    }

    @POST
    @Path("/{id}/cod/collected")
    @RolesAllowed({"ADMIN", "STAFF"})
    @Operation(summary = "Xac nhan shipper da thu tien COD")
    public Payment confirmCodCollected(@PathParam("id") Long id, @QueryParam("note") String note) {
        return paymentService.confirmCodCollected(id, note);
    }

    /**
     * Ghi nhan hoan tien trong moi truong Sandbox. Khi chuyen sang production,
     * endpoint nay can duoc noi voi Refund API cua tung cong (MoMo/VNPay).
     */
    @POST
    @Path("/{id}/refund")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Ghi nhan hoan tien giao dich (Sandbox)")
    public Payment refund(@PathParam("id") Long id, @QueryParam("note") String note) {
        return paymentService.refundSandbox(id, note);
    }

    private void requireOwnPayment(Payment payment) {
        if (identity.hasRole("ADMIN")) return;
        requireCurrentUser(payment.userId);
    }

    private void requireCurrentUser(Long userId) {
        if (identity.hasRole("ADMIN")) return;
        Long authenticatedId = authenticatedUserId();
        if (userId == null || !userId.equals(authenticatedId)) {
            throw new ForbiddenException("Bạn không có quyền thao tác thanh toán của người dùng khác");
        }
    }

    private Long authenticatedUserId() {
        if (identity.getPrincipal() instanceof JsonWebToken token) {
            Object claim = token.getClaim("userId");
            if (claim instanceof Number number) return number.longValue();
            try { return claim == null ? null : Long.valueOf(String.valueOf(claim)); }
            catch (NumberFormatException ignored) { }
        }
        return null;
    }
}

package com.shop.payment.service;

import com.shop.payment.client.MomoClient;
import com.shop.payment.client.OrderClient;
import com.shop.payment.client.ZaloPayClient;
import com.shop.payment.dto.*;
import com.shop.payment.entity.Payment;
import com.shop.payment.entity.PaymentMethod;
import com.shop.payment.entity.PaymentStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PaymentService — logic chinh cua thanh toan MoMo.
 * <p>
 * Quy trinh:
 *   1. createPayment(): tao Payment, goi MoMo /create, luu payUrl + qrCodeUrl,
 *      tra ve cho frontend hien thi QR.
 *   2. handleIpn(): MoMo POST ve khi user thanh toan xong → verify signature →
 *      cap nhat Payment.status → goi sang order-service de chuyen order
 *      sang trang thai CONFIRMED.
 *   3. queryStatus(): polling thu cong, dung khi mat IPN hoac de chu dong check.
 */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    // ---- Cau hinh MoMo (lay tu application.properties) ----
    @ConfigProperty(name = "momo.partner-code")
    String partnerCode;

    @ConfigProperty(name = "momo.access-key")
    String accessKey;

    @ConfigProperty(name = "momo.secret-key")
    String secretKey;

    @ConfigProperty(name = "momo.ipn-url")
    String ipnUrl;

    @ConfigProperty(name = "momo.redirect-url")
    String redirectUrl;

    @ConfigProperty(name = "vnpay.tmn-code", defaultValue = "") String vnpayTmnCode;
    @ConfigProperty(name = "vnpay.hash-secret", defaultValue = "") String vnpayHashSecret;
    @ConfigProperty(name = "vnpay.payment-url") String vnpayPaymentUrl;
    @ConfigProperty(name = "vnpay.return-url") String vnpayReturnUrl;

    @ConfigProperty(name = "zalopay.app-id", defaultValue = "") String zaloPayAppId;
    @ConfigProperty(name = "zalopay.key1", defaultValue = "") String zaloPayKey1;
    @ConfigProperty(name = "zalopay.key2", defaultValue = "") String zaloPayKey2;
    @ConfigProperty(name = "zalopay.callback-url") String zaloPayCallbackUrl;
    @ConfigProperty(name = "zalopay.redirect-url") String zaloPayRedirectUrl;

    @RestClient
    MomoClient momoClient;

    @RestClient
    ZaloPayClient zaloPayClient;

    @RestClient
    OrderClient orderClient;

    // -------------------------------------------------------------
    // 1. TAO GIAO DICH
    // -------------------------------------------------------------

    /**
     * Tao Payment + goi MoMo de lay payUrl/qrCodeUrl/deeplink.
     * <p>
     * Khong dung @Transactional o method nay — chia thanh nhieu transaction
     * rieng biet bang QuarkusTransaction.requiringNew() de:
     *   1. Neu MoMo tu choi, Payment van duoc luu (status=FAILED) cho audit.
     *   2. Khong giu DB connection trong luc cho MoMo phan hoi.
     */
    public Payment createPayment(CreatePaymentRequest req) {
        // Step 1: tao Payment record PENDING (transaction 1, commit ngay)
        Payment payment = QuarkusTransaction.requiringNew().call(() -> {
            long ts = System.currentTimeMillis();
            Payment p = new Payment();
            p.requestId = UUID.randomUUID().toString();
            p.momoOrderId = "SHOP_" + req.orderId + "_" + ts;
            p.orderId = req.orderId;
            p.userId = req.userId;
            p.amount = req.amount;
            p.orderInfo = req.orderInfo != null
                    ? req.orderInfo
                    : "Thanh toan don hang #" + req.orderId;
            p.method = req.method;
            p.status = PaymentStatus.PENDING;
            p.createdAt = Instant.now();
            p.persist();
            return p;
        });

        // COD khong phai la giao dich MoMo. Luu lai de doi shipper/admin xac nhan
        // da thu tien; tuyet doi khong danh dau SUCCESS ngay khi dat don.
        if (payment.method == PaymentMethod.COD) {
            return QuarkusTransaction.requiringNew().call(() -> {
                Payment p = Payment.findById(payment.id);
                p.message = "Cho thu tien khi giao hang (COD)";
                return p;
            });
        }

        if (payment.method == PaymentMethod.VNPAY) {
            if (vnpayTmnCode.isBlank() || vnpayHashSecret.isBlank()) {
                QuarkusTransaction.requiringNew().run(() -> { Payment p = Payment.findById(payment.id); p.status = PaymentStatus.FAILED; p.message = "Chưa cấu hình VNPay Sandbox"; });
                throw new WebApplicationException("Chưa cấu hình VNPay Sandbox", Response.Status.SERVICE_UNAVAILABLE);
            }
            return QuarkusTransaction.requiringNew().call(() -> {
                Payment p = Payment.findById(payment.id);
                p.momoOrderId = "PZ" + p.id + System.currentTimeMillis();
                p.payUrl = buildVnpayUrl(p);
                p.message = "Đang chờ thanh toán qua VNPay";
                return p;
            });
        }

        if (payment.method == PaymentMethod.ZALOPAY) {
            if (!zaloPayConfigured()) {
                QuarkusTransaction.requiringNew().run(() -> {
                    Payment p = Payment.findById(payment.id);
                    p.status = PaymentStatus.FAILED;
                    p.message = "Chưa cấu hình ZaloPay Sandbox";
                });
                throw new WebApplicationException("Chưa cấu hình ZaloPay Sandbox", Response.Status.SERVICE_UNAVAILABLE);
            }
            return createZaloPayPayment(payment);
        }

        if (payment.method != PaymentMethod.MOMO) {
            throw new WebApplicationException(
                    "Phuong thuc thanh toan nay chua duoc ho tro",
                    Response.Status.BAD_REQUEST);
        }

        // Step 2: goi MoMo (KHONG nam trong transaction)
        MomoCreateResponse resp;
        try {
            MomoCreateRequest momoReq = buildMomoCreateRequest(payment);
            resp = momoClient.create(momoReq);
            LOG.infof("MoMo response: resultCode=%d, message=%s",
                    resp.resultCode, resp.message);
        } catch (Exception e) {
            LOG.errorf(e, "Loi khi goi MoMo create: %s", e.getMessage());
            // Cap nhat status FAILED trong transaction rieng
            Long pid = payment.id;
            QuarkusTransaction.requiringNew().run(() -> {
                Payment p = Payment.findById(pid);
                if (p != null) {
                    p.status = PaymentStatus.FAILED;
                    p.message = "Khong ket noi duoc MoMo: " + e.getMessage();
                }
            });
            throw new WebApplicationException(
                    "Khong khoi tao duoc thanh toan",
                    Response.Status.BAD_GATEWAY);
        }

        // Step 3: luu ket qua tu MoMo (transaction 2)
        final MomoCreateResponse finalResp = resp;
        return QuarkusTransaction.requiringNew().call(() -> {
            Payment p = Payment.findById(payment.id);
            p.resultCode = finalResp.resultCode;
            p.message = finalResp.message;

            if (finalResp.resultCode != null && finalResp.resultCode == 0) {
                p.payUrl = finalResp.payUrl;
                p.qrCodeUrl = finalResp.qrCodeUrl;
                p.deeplink = finalResp.deeplink;
                // status van la PENDING — cho user quet QR
            } else {
                p.status = PaymentStatus.FAILED;
                throw new WebApplicationException(
                        "MoMo tu choi giao dich: " + finalResp.message,
                        Response.Status.BAD_REQUEST);
            }
            return p;
        });
    }

    private boolean zaloPayConfigured() {
        return !zaloPayAppId.isBlank() && !zaloPayKey1.isBlank() && !zaloPayKey2.isBlank()
                && !"disabled".equalsIgnoreCase(zaloPayAppId)
                && !"disabled".equalsIgnoreCase(zaloPayKey1)
                && !"disabled".equalsIgnoreCase(zaloPayKey2);
    }

    private MomoCreateRequest buildMomoCreateRequest(Payment payment) {
        MomoCreateRequest req = new MomoCreateRequest();
        req.partnerCode = partnerCode;
        req.partnerName = "Shop";
        req.storeId = "ShopStore";
        req.requestId = payment.requestId;
        req.amount = payment.amount;
        req.orderId = payment.momoOrderId;
        req.orderInfo = payment.orderInfo;
        req.redirectUrl = redirectUrl;
        req.ipnUrl = ipnUrl;
        req.requestType = "captureWallet";
        req.extraData = "";
        req.lang = "vi";
        req.signature = MomoSignature.signCreateRequest(
                accessKey, secretKey,
                req.amount, req.extraData, req.ipnUrl,
                req.orderId, req.orderInfo, req.partnerCode,
                req.redirectUrl, req.requestId, req.requestType);
        return req;
    }

    private String buildVnpayUrl(Payment p) {
        var now = java.time.ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        Map<String,String> data = new LinkedHashMap<>();
        data.put("vnp_Version", "2.1.0"); data.put("vnp_Command", "pay"); data.put("vnp_TmnCode", vnpayTmnCode);
        data.put("vnp_Amount", String.valueOf(Math.multiplyExact(p.amount, 100))); data.put("vnp_CurrCode", "VND");
        data.put("vnp_TxnRef", p.momoOrderId); data.put("vnp_OrderInfo", "Thanh toan don hang " + p.orderId);
        data.put("vnp_OrderType", "other"); data.put("vnp_Locale", "vn"); data.put("vnp_ReturnUrl", vnpayReturnUrl);
        data.put("vnp_IpAddr", "127.0.0.1"); data.put("vnp_CreateDate", fmt.format(now)); data.put("vnp_ExpireDate", fmt.format(now.plusMinutes(15)));
        String query = VnpaySignature.query(data); return vnpayPaymentUrl + "?" + query + "&vnp_SecureHash=" + VnpaySignature.hmac(query, vnpayHashSecret);
    }

    /** Creates a ZaloPay Sandbox order and returns its QR/order URL to the client. */
    private Payment createZaloPayPayment(Payment payment) {
        final long now = System.currentTimeMillis();
        final String zaloTransactionId = DateTimeFormatter.ofPattern("yyMMdd")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.ofEpochMilli(now))
                + "_PZ" + payment.id + "_" + (now % 1_000_000);
        Payment requestPayment = QuarkusTransaction.requiringNew().call(() -> {
            Payment p = Payment.findById(payment.id);
            p.momoOrderId = zaloTransactionId; // shared merchant transaction reference column
            return p;
        });
        Map<String, Object> request = buildZaloPayRequest(requestPayment, now);
        Map<String, Object> result;
        try {
            result = zaloPayClient.create(request);
        } catch (Exception e) {
            markPaymentFailed(payment.id, "Không kết nối được ZaloPay: " + e.getMessage());
            throw new WebApplicationException("Không khởi tạo được ZaloPay", Response.Status.BAD_GATEWAY);
        }
        if (number(result.get("return_code")) != 1) {
            String message = string(result.get("return_message"), "ZaloPay từ chối tạo giao dịch");
            markPaymentFailed(payment.id, message);
            throw new WebApplicationException(message, Response.Status.BAD_REQUEST);
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            Payment p = Payment.findById(payment.id);
            p.payUrl = string(result.get("order_url"), "");
            p.qrCodeUrl = string(result.get("qr_code"), "");
            p.deeplink = string(result.get("zp_trans_token"), "");
            p.message = "Đang chờ thanh toán qua ZaloPay";
            return p;
        });
    }

    private Map<String, Object> buildZaloPayRequest(Payment p, long appTime) {
        String embedData = "{\"redirecturl\":\"" + zaloPayRedirectUrl + "\",\"preferred_payment_method\":[\"zalopay_wallet\"]}";
        String item = "[]";
        String input = zaloPayAppId + "|" + p.momoOrderId + "|" + p.userId + "|" + p.amount + "|" + appTime + "|" + embedData + "|" + item;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app_id", Integer.parseInt(zaloPayAppId));
        body.put("app_user", String.valueOf(p.userId));
        body.put("app_trans_id", p.momoOrderId);
        body.put("app_time", appTime);
        body.put("expire_duration_seconds", 900);
        body.put("amount", p.amount);
        body.put("description", "PhoneZone - đơn hàng " + p.orderId);
        body.put("callback_url", zaloPayCallbackUrl);
        body.put("item", item);
        body.put("embed_data", embedData);
        body.put("bank_code", "");
        body.put("mac", ZaloPaySignature.hmacSha256(input, zaloPayKey1));
        return body;
    }

    private void markPaymentFailed(Long paymentId, String message) {
        QuarkusTransaction.requiringNew().run(() -> {
            Payment p = Payment.findById(paymentId);
            if (p != null) { p.status = PaymentStatus.FAILED; p.message = message; }
        });
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }
    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    @Transactional
    public boolean handleVnpayReturn(Map<String,String> data) {
        String hash = data.get("vnp_SecureHash"); if (hash == null) return false;
        Map<String,String> signed = new LinkedHashMap<>(data); signed.remove("vnp_SecureHash"); signed.remove("vnp_SecureHashType");
        if (!VnpaySignature.hmac(VnpaySignature.query(signed), vnpayHashSecret).equalsIgnoreCase(hash)) return false;
        Payment p = Payment.find("momoOrderId", data.get("vnp_TxnRef")).firstResult();
        if (p == null) return false;
        // VNPay may resend the browser return URL. Do not turn an already successful payment into a failure.
        if (p.status != PaymentStatus.PENDING) return p.status == PaymentStatus.SUCCESS;
        p.resultCode = parseCode(data.get("vnp_ResponseCode")); p.transactionId = data.get("vnp_TransactionNo");
        if ("00".equals(data.get("vnp_ResponseCode")) && "00".equals(data.get("vnp_TransactionStatus"))) { p.status = PaymentStatus.SUCCESS; p.message = "VNPay thanh toán thành công"; notifyOrderService(p.orderId, "CONFIRMED"); }
        else { p.status = PaymentStatus.FAILED; p.message = "VNPay từ chối hoặc hủy giao dịch"; }
        return true;
    }

    public Payment findByGatewayReference(String reference) {
        return reference == null || reference.isBlank() ? null : Payment.findByMomoOrderId(reference);
    }
    private Integer parseCode(String value) { try { return value == null ? null : Integer.valueOf(value); } catch (NumberFormatException e) { return null; } }

    // -------------------------------------------------------------
    // 2. XU LY IPN (MoMo callback)
    // -------------------------------------------------------------

    /**
     * Xu ly IPN tu MoMo. Goi tu PaymentResource.
     * Tra ve {status: "ok"} de MoMo biet da nhan duoc.
     */
    @Transactional
    public boolean handleIpn(MomoIpnRequest ipn) {
        LOG.infof("Nhan IPN: orderId=%s, requestId=%s, resultCode=%d, transId=%d",
                ipn.orderId, ipn.requestId, ipn.resultCode, ipn.transId);

        // 1. Verify signature truoc khi tin du lieu
        String expectedSig = MomoSignature.signIpn(
                accessKey, secretKey,
                ipn.amount, ipn.extraData, ipn.message,
                ipn.orderId, ipn.orderInfo, ipn.orderType,
                ipn.partnerCode, ipn.payType, ipn.requestId,
                ipn.responseTime, ipn.resultCode, ipn.transId);

        if (!expectedSig.equals(ipn.signature)) {
            LOG.errorf("Signature khong khop! expected=%s, got=%s",
                    expectedSig, ipn.signature);
            // Theo docs cua MoMo: van phai tra 204 No Content, nhung khong xu ly
            return false;
        }

        // 2. Tim Payment trong DB
        Payment payment = Payment.findByRequestId(ipn.requestId);
        if (payment == null) {
            LOG.warnf("Khong tim thay Payment cho requestId=%s", ipn.requestId);
            return false;
        }

        // 3. Idempotency: neu da xu ly roi thi bo qua (MoMo co the goi lai)
        if (payment.status != PaymentStatus.PENDING) {
            LOG.infof("Payment %s da co status=%s, bo qua IPN nay",
                    payment.id, payment.status);
            return true;
        }

        // 4. Kiem tra amount match (chong gia mao)
        if (!payment.amount.equals(ipn.amount)) {
            LOG.errorf("Amount khong khop! payment.amount=%d, ipn.amount=%d",
                    payment.amount, ipn.amount);
            payment.status = PaymentStatus.FAILED;
            payment.message = "Amount khong khop";
            return false;
        }

        // 5. Cap nhat trang thai dua tren resultCode
        payment.resultCode = ipn.resultCode;
        payment.message = ipn.message;
        payment.transactionId = String.valueOf(ipn.transId);

        if (ipn.resultCode == 0) {
            payment.status = PaymentStatus.SUCCESS;
            LOG.infof("Payment %s THANH CONG", payment.id);
            // Bao cho order-service biet
            notifyOrderService(payment.orderId, "CONFIRMED");
        } else {
            payment.status = PaymentStatus.FAILED;
            LOG.infof("Payment %s THAT BAI: %s", payment.id, ipn.message);
            notifyOrderService(payment.orderId, "CANCELLED");
        }

        return true;
    }

    /** Goi sang order-service de cap nhat trang thai don hang */
    private void notifyOrderService(Long orderId, String status) {
        try {
            orderClient.updateStatus(orderId, status);
            LOG.infof("Da bao order-service: order=%d -> %s", orderId, status);
        } catch (Exception e) {
            // Khong throw — neu order-service down, payment van phai luu
            // (co the retry sau bang scheduled job)
            LOG.errorf(e, "Khong cap nhat duoc trang thai order %d", orderId);
        }
    }

    // -------------------------------------------------------------
    // 3. QUERY (polling thu cong)
    // -------------------------------------------------------------

    /**
     * Goi MoMo de hoi trang thai giao dich. Dung khi:
     *   - User da quay lai trang web nhung IPN chua ve
     *   - Admin muon kiem tra
     *   - Scheduled job kiem tra cac payment qua lau van PENDING
     * <p>
     * KHONG dung @Transactional o entire method vi co I/O sang MoMo.
     * Doc va ghi DB duoc tach thanh transaction nho.
     */
    public Payment queryAndSync(Long paymentId) {
        // 1. Doc Payment trong transaction nho
        Payment payment = QuarkusTransaction.requiringNew().call(() -> {
            Payment p = Payment.findById(paymentId);
            if (p == null) {
                throw new NotFoundException("Payment khong ton tai: " + paymentId);
            }
            return p;
        });

        // Neu da xong thi khong can goi lai
        if (payment.status == PaymentStatus.SUCCESS
                || payment.status == PaymentStatus.FAILED) {
            return payment;
        }
        if (payment.method == PaymentMethod.ZALOPAY) {
            return queryZaloPayAndSync(payment);
        }
        if (payment.method != PaymentMethod.MOMO) {
            return payment;
        }

        // 2. Goi MoMo query (KHONG nam trong transaction)
        MomoQueryRequest req = new MomoQueryRequest();
        req.partnerCode = partnerCode;
        req.requestId = payment.requestId;
        req.orderId = payment.momoOrderId;
        req.lang = "vi";
        req.signature = MomoSignature.signQueryRequest(
                accessKey, secretKey,
                req.orderId, req.partnerCode, req.requestId);

        MomoQueryResponse resp;
        try {
            resp = momoClient.query(req);
            LOG.infof("Query MoMo: resultCode=%d, message=%s",
                    resp.resultCode, resp.message);
        } catch (Exception e) {
            LOG.errorf(e, "Loi khi query MoMo: %s", e.getMessage());
            return payment;  // tra ve nguyen trang
        }

        // 3. Cap nhat Payment trong transaction moi
        final MomoQueryResponse finalResp = resp;
        Long pid = payment.id;
        return QuarkusTransaction.requiringNew().call(() -> {
            Payment p = Payment.findById(pid);
            p.resultCode = finalResp.resultCode;
            p.message = finalResp.message;

            boolean shouldNotifyConfirmed = false;
            if (finalResp.resultCode != null && finalResp.resultCode == 0) {
                p.status = PaymentStatus.SUCCESS;
                p.transactionId = String.valueOf(finalResp.transId);
                shouldNotifyConfirmed = true;
            } else if (finalResp.resultCode != null && finalResp.resultCode == 1006) {
                // 1006 = user huy → de la PENDING, cho retry
            } else if (finalResp.resultCode != null && finalResp.resultCode != 1000) {
                // 1000 = giao dich dang xu ly
                p.status = PaymentStatus.FAILED;
            }

            // Goi order-service NGOAI transaction (sau khi commit) thuong an toan hon
            // Nhung de don gian gio goi luon, neu fail thi log
            if (shouldNotifyConfirmed) {
                notifyOrderService(p.orderId, "CONFIRMED");
            }
            return p;
        });
    }

    private Payment queryZaloPayAndSync(Payment payment) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("app_id", Integer.parseInt(zaloPayAppId));
        request.put("app_trans_id", payment.momoOrderId);
        request.put("mac", ZaloPaySignature.hmacSha256(zaloPayAppId + "|" + payment.momoOrderId + "|" + zaloPayKey1, zaloPayKey1));
        Map<String, Object> result;
        try { result = zaloPayClient.query(request); }
        catch (Exception e) { LOG.warnf("Không query được ZaloPay %d: %s", payment.id, e.getMessage()); return payment; }
        final int code = number(result.get("return_code"));
        final String transactionId = string(result.get("zp_trans_id"), null);
        final String message = string(result.get("return_message"), "Đang chờ ZaloPay");
        if (code == 3) return payment; // processing
        return QuarkusTransaction.requiringNew().call(() -> {
            Payment p = Payment.findById(payment.id);
            p.resultCode = code; p.message = message;
            if (code == 1) { p.status = PaymentStatus.SUCCESS; p.transactionId = transactionId; notifyOrderService(p.orderId, "CONFIRMED"); }
            else if (code == 2) p.status = PaymentStatus.FAILED;
            return p;
        });
    }

    /** ZaloPay calls this endpoint server-to-server; signature is verified with key2. */
    @Transactional
    public boolean handleZaloPayCallback(String data, String mac) {
        if (data == null || mac == null || !ZaloPaySignature.hmacSha256(data, zaloPayKey2).equalsIgnoreCase(mac)) return false;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(data);
            String transactionRef = node.path("app_trans_id").asText();
            Payment p = Payment.findByMomoOrderId(transactionRef);
            if (p == null || p.status != PaymentStatus.PENDING) return false;
            long amount = node.path("amount").asLong(-1);
            if (amount != p.amount) { p.status = PaymentStatus.FAILED; p.message = "ZaloPay callback sai số tiền"; return false; }
            p.status = PaymentStatus.SUCCESS;
            p.transactionId = node.path("zp_trans_id").asText(null);
            p.message = "ZaloPay thanh toán thành công";
            notifyOrderService(p.orderId, "CONFIRMED");
            return true;
        } catch (Exception e) { LOG.warnf("ZaloPay callback không hợp lệ: %s", e.getMessage()); return false; }
    }

    // -------------------------------------------------------------
    // 4. Cac thao tac CRUD don gian
    // -------------------------------------------------------------

    public Payment getById(Long id) {
        Payment p = Payment.findById(id);
        if (p == null) throw new NotFoundException("Payment khong ton tai: " + id);
        return p;
    }

    public List<Payment> getByOrderId(Long orderId) {
        return Payment.findByOrderId(orderId);
    }

    public List<Payment> getAll() {
        return Payment.listAll();
    }

    /** Cap nhat thu cong (cho admin) */
    @Transactional
    public Payment manualConfirm(Long paymentId, boolean success, String note) {
        Payment p = getById(paymentId);
        if (p.status != PaymentStatus.PENDING) {
            throw new WebApplicationException(
                    "Payment khong o trang thai PENDING, khong xac nhan thu cong duoc",
                    Response.Status.CONFLICT);
        }
        p.status = success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        p.message = "[ADMIN] " + (note != null ? note : "");
        notifyOrderService(p.orderId, success ? "CONFIRMED" : "CANCELLED");
        return p;
    }

    /** Shipper/admin xac nhan da thu tien mat tu don COD. */
    @Transactional
    public Payment confirmCodCollected(Long paymentId, String note) {
        Payment p = getById(paymentId);
        if (p.method != PaymentMethod.COD) {
            throw new WebApplicationException("Day khong phai thanh toan COD", Response.Status.BAD_REQUEST);
        }
        if (p.status != PaymentStatus.PENDING) {
            throw new WebApplicationException("Thanh toan COD da duoc xu ly", Response.Status.CONFLICT);
        }
        p.status = PaymentStatus.SUCCESS;
        p.message = "[COD DA THU] " + (note != null ? note : "Da nhan tien tu khach");
        notifyOrderService(p.orderId, "CONFIRMED");
        return p;
    }

    /**
     * Sandbox chi ghi nhan ket qua hoan tien vao so cai noi bo; khong duoc gia
     * lap la da chuyen tien that. Production phai goi API refund co chu ky cua
     * MoMo/VNPay truoc, chi cap nhat REFUNDED sau khi cong thanh toan xac nhan.
     */
    @Transactional
    public Payment refundSandbox(Long paymentId, String note) {
        Payment p = getById(paymentId);
        if (p.status != PaymentStatus.SUCCESS) {
            throw new WebApplicationException(
                    "Chi giao dich da thanh toan moi duoc hoan tien",
                    Response.Status.CONFLICT);
        }
        p.status = PaymentStatus.REFUNDED;
        p.message = "[SANDBOX HOAN TIEN] "
                + (note != null && !note.isBlank() ? note : "Da ghi nhan hoan tien boi quan tri vien");
        return p;
    }
}

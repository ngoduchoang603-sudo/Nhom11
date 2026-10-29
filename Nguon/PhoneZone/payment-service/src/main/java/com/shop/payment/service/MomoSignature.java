package com.shop.payment.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Utility tinh chu ky HMAC-SHA256 cho MoMo API.
 * <p>
 * QUAN TRONG: thu tu cac field trong raw signature data PHAI dung
 * theo docs cua MoMo, neu sai mot dau "=" hoac mot khoang trang
 * MoMo se tra ve resultCode 99 "Unknown error" — rat kho debug.
 * <p>
 * Tham khao: https://developers.momo.vn/v3/vi/docs/payment/api/wallet/onetime/
 */
public class MomoSignature {

    /**
     * Tinh signature cho request createPayment (captureWallet).
     * <p>
     * Raw data theo dung thu tu:
     * accessKey=...&amount=...&extraData=...&ipnUrl=...&orderId=...
     * &orderInfo=...&partnerCode=...&redirectUrl=...&requestId=...&requestType=...
     */
    public static String signCreateRequest(
            String accessKey,
            String secretKey,
            Long amount,
            String extraData,
            String ipnUrl,
            String orderId,
            String orderInfo,
            String partnerCode,
            String redirectUrl,
            String requestId,
            String requestType) {

        String rawData = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + (extraData == null ? "" : extraData)
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + requestType;

        return hmacSha256(secretKey, rawData);
    }

    /**
     * Tinh signature cho IPN ma MoMo gui ve.
     * <p>
     * Raw data theo dung thu tu:
     * accessKey=...&amount=...&extraData=...&message=...&orderId=...
     * &orderInfo=...&orderType=...&partnerCode=...&payType=...&requestId=...
     * &responseTime=...&resultCode=...&transId=...
     */
    public static String signIpn(
            String accessKey,
            String secretKey,
            Long amount,
            String extraData,
            String message,
            String orderId,
            String orderInfo,
            String orderType,
            String partnerCode,
            String payType,
            String requestId,
            Long responseTime,
            Integer resultCode,
            Long transId) {

        String rawData = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + (extraData == null ? "" : extraData)
                + "&message=" + message
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&orderType=" + (orderType == null ? "" : orderType)
                + "&partnerCode=" + partnerCode
                + "&payType=" + (payType == null ? "" : payType)
                + "&requestId=" + requestId
                + "&responseTime=" + responseTime
                + "&resultCode=" + resultCode
                + "&transId=" + transId;

        return hmacSha256(secretKey, rawData);
    }

    /**
     * Tinh signature cho request query trang thai giao dich.
     * <p>
     * Raw data: accessKey=...&orderId=...&partnerCode=...&requestId=...
     */
    public static String signQueryRequest(
            String accessKey,
            String secretKey,
            String orderId,
            String partnerCode,
            String requestId) {

        String rawData = "accessKey=" + accessKey
                + "&orderId=" + orderId
                + "&partnerCode=" + partnerCode
                + "&requestId=" + requestId;

        return hmacSha256(secretKey, rawData);
    }

    /** Tinh HMAC-SHA256 va tra ve hex string */
    private static String hmacSha256(String secretKey, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Loi khi tinh HMAC-SHA256", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

package com.shop.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IPN (Instant Payment Notification) — body ma MoMo POST ve ipnUrl
 * sau khi giao dich co ket qua (thanh cong hoac that bai).
 * <p>
 * QUAN TRONG: phai verify chu ky (signature) truoc khi tin so lieu nay,
 * vi bat ky ai cung co the goi den endpoint ipnUrl.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MomoIpnRequest {
    public String partnerCode;
    public String orderId;
    public String requestId;
    public Long amount;
    public String orderInfo;
    public String orderType;
    public Long transId;            // ID giao dich cua MoMo
    public Integer resultCode;
    public String message;
    public String payType;
    public Long responseTime;
    public String extraData;
    public String signature;
}

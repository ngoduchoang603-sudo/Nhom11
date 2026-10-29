package com.shop.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phan hoi tu MoMo sau khi goi /v2/gateway/api/create.
 * <p>
 * Cac truong quan trong:
 *   resultCode: 0 = thanh cong, khac 0 = loi
 *   payUrl:     URL de mo web thanh toan (PC)
 *   qrCodeUrl:  URL hoac chuoi QR code de hien thi
 *   deeplink:   Mo truc tiep app MoMo (mobile)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MomoCreateResponse {
    public String partnerCode;
    public String requestId;
    public String orderId;
    public Long amount;
    public Long responseTime;
    public String message;
    public Integer resultCode;
    public String payUrl;
    public String deeplink;
    public String qrCodeUrl;
    public String deeplinkMiniApp;
}

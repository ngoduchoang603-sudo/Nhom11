package com.shop.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body gui sang MoMo API /v2/gateway/api/create
 * <p>
 * Cac truong bat buoc theo docs:
 *   partnerCode, requestId, amount, orderId, orderInfo,
 *   redirectUrl, ipnUrl, requestType, extraData, lang, signature
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MomoCreateRequest {
    public String partnerCode;
    public String partnerName;
    public String storeId;
    public String requestId;
    public Long amount;
    public String orderId;
    public String orderInfo;
    public String redirectUrl;
    public String ipnUrl;
    public String requestType = "captureWallet";   // payWithMethod, captureWallet, ...
    public String extraData = "";
    public String lang = "vi";
    public String signature;
}

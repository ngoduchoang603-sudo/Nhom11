package com.shop.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Goi MoMo API /v2/gateway/api/query de kiem tra trang thai giao dich.
 * Dung cho truong hop bi mat IPN hoac de polling.
 */
public class MomoQueryRequest {
    public String partnerCode;
    public String requestId;
    public String orderId;
    public String lang = "vi";
    public String signature;
}

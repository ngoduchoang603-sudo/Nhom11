package com.shop.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MomoQueryResponse {
    public String partnerCode;
    public String orderId;
    public String requestId;
    public String extraData;
    public Long amount;
    public Long transId;
    public String payType;
    public Integer resultCode;
    public String message;
    public Long responseTime;
}

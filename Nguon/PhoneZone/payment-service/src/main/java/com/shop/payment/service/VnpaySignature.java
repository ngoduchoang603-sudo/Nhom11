package com.shop.payment.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

final class VnpaySignature {
    private VnpaySignature() {}
    static String query(Map<String, String> input) {
        StringBuilder out = new StringBuilder();
        for (var e : new TreeMap<>(input).entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            if (!out.isEmpty()) out.append('&');
            out.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII)).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return out.toString();
    }
    static String hmac(String value, String secret) {
        try { Mac mac = Mac.getInstance("HmacSHA512"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512")); byte[] raw = mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for(byte b:raw) out.append(String.format("%02x", b)); return out.toString(); }
        catch (Exception e) { throw new IllegalStateException("Không thể ký VNPay", e); }
    }
}

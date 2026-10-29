package com.shop.agent.application;

import java.text.Normalizer;

/** Deterministic first-pass router; the LLM never chooses write tools. */
public final class AgentIntentRouter {
    public enum Intent { ORDER_TRACKING, CART_SUMMARY, DEAL, ORDER_MUTATION, CART_MUTATION, COMPARISON, RECOMMENDATION, VARIANT, PRODUCT_FOLLOW_UP, CATALOG, GENERAL }
    private AgentIntentRouter() { }
    public static Intent route(String question) {
        String text = question == null ? "" : Normalizer.normalize(question, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').toLowerCase();
        // PhoneZone only sells phones. Never turn a laptop request into made-up phone advice.
        if (text.contains("laptop") || text.contains("may tinh bang") || text.contains("tablet")) return Intent.GENERAL;
        if (text.contains("don") && (text.contains("dau") || text.contains("theo doi") || text.contains("trang thai") || text.contains("giao")
                || text.contains("kiem tra") || text.contains("tra cuu") || text.contains("xem don"))) return Intent.ORDER_TRACKING;
        if (text.contains("gio hang") && (text.contains("xem") || text.contains("co gi") || text.contains("bao nhieu") || text.contains("tong"))) return Intent.CART_SUMMARY;
        if (text.contains("ma giam") || text.contains("coupon") || text.contains("khuyen mai")) return Intent.DEAL;
        if (text.contains("huy don") || text.contains("tao don") || text.contains("dat hang")
                || text.contains("dat mua") || text.contains("muon mua") || text.contains("mua ngay")
                || text.contains("chot don") || text.contains("chot mua") || text.contains("lay may nay") || text.contains("mua con nay")) return Intent.ORDER_MUTATION;
        if ((text.contains("them") || text.contains("bo")) && (text.contains("gio") || text.contains("cart"))
                || text.contains("cho vao gio") || text.contains("lay con nay vao gio")) return Intent.CART_MUTATION;
        if (text.contains("so sanh") || text.contains("khac nhau") || text.contains("nen chon may nao")
                || text.contains("2 dien thoai tren") || text.contains("hai dien thoai tren") || text.contains("2 con nay")
                || text.contains("hai con nay") || text.contains("2 may tren") || text.contains("hai may tren") || text.contains("cai nao hon")) return Intent.COMPARISON;
        if (text.contains("hop voi toi") || text.contains("goi y") || text.contains("de xuat") || text.contains("phu hop voi toi")) return Intent.RECOMMENDATION;
        if (text.contains("mau") || text.contains("den") || text.contains("trang")) return Intent.VARIANT;
        // Resolve a product pronoun from server-side session memory rather than
        // issuing a broad catalog search that could select the wrong phone.
        if (text.contains("con do") || text.contains("may do") || text.contains("san pham do") || text.contains("con vua tu van")) return Intent.PRODUCT_FOLLOW_UP;
        return text.matches("(?s).*(san pham|dien thoai|gia|ton kho|con hang|iphone|samsung|xiaomi|oppo|vivo|realme|game|gaming|camera|chup|pin|hieu nang|muot|ngan sach|trieu).*") ? Intent.CATALOG : Intent.GENERAL;
    }
}

package com.shop.agent.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fast regression suite for minimum manager tool-selection coverage. */
class AgentIntentCoverageTest {
    @Test void productAndDealQueriesReachDedicatedTools() {
        assertEquals(AgentIntentRouter.Intent.CATALOG, AgentIntentRouter.route("iPhone nào còn hàng"));
        assertEquals(AgentIntentRouter.Intent.DEAL, AgentIntentRouter.route("có mã giảm giá nào không"));
    }
    @Test void cartAndOrderWritesNeverSkipConfirmationRoute() {
        assertEquals(AgentIntentRouter.Intent.CART_MUTATION, AgentIntentRouter.route("thêm iPhone vào giỏ"));
        assertEquals(AgentIntentRouter.Intent.ORDER_MUTATION, AgentIntentRouter.route("đặt hàng cho tôi"));
        assertEquals(AgentIntentRouter.Intent.ORDER_MUTATION, AgentIntentRouter.route("hủy đơn này"));
    }
    @Test void productComparisonAndDeliveryUseReadOnlyRoutes() {
        assertEquals(AgentIntentRouter.Intent.COMPARISON, AgentIntentRouter.route("so sánh iphone và samsung"));
        assertEquals(AgentIntentRouter.Intent.ORDER_TRACKING, AgentIntentRouter.route("đơn hàng của tôi đến đâu rồi"));
    }
}

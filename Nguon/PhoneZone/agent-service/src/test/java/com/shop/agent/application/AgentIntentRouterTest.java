package com.shop.agent.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentIntentRouterTest {
    @Test void routesOrderQuestionToReadOnlyOrderTool() { assertEquals(AgentIntentRouter.Intent.ORDER_TRACKING, AgentIntentRouter.route("Đơn hàng của tôi đang ở đâu?")); }
    @Test void routesCouponQuestionToDealTool() { assertEquals(AgentIntentRouter.Intent.DEAL, AgentIntentRouter.route("Có mã giảm giá nào không?")); }
    @Test void routesComparisonToComparisonTool() { assertEquals(AgentIntentRouter.Intent.COMPARISON, AgentIntentRouter.route("So sánh iPhone và Samsung")); }
    @Test void blocksOrderMutationFromAutomaticWrite() { assertEquals(AgentIntentRouter.Intent.ORDER_MUTATION, AgentIntentRouter.route("Hủy đơn cho tôi")); }
    @Test void doesNotRouteLaptopAdviceToCatalog() { assertEquals(AgentIntentRouter.Intent.GENERAL, AgentIntentRouter.route("Tư vấn laptop gaming")); }
    @Test void routesProductPronounToSessionFollowUp() { assertEquals(AgentIntentRouter.Intent.PRODUCT_FOLLOW_UP, AgentIntentRouter.route("Máy đó còn hàng không?")); }
}

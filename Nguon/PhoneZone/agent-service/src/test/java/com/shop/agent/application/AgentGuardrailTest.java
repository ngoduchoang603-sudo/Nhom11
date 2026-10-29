package com.shop.agent.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression checks: dangerous intents are never routed to an automatic write path. */
class AgentGuardrailTest {
    @Test void laptopQuestionIsNotInventedAsPhoneCatalogData() {
        assertEquals(AgentIntentRouter.Intent.GENERAL, AgentIntentRouter.route("Tu van laptop gaming"));
    }

    @Test void cancelOrderAlwaysRequiresHumanConfirmation() {
        assertEquals(AgentIntentRouter.Intent.ORDER_MUTATION, AgentIntentRouter.route("Huy don PZ-123 cho toi"));
    }

    @Test void phoneQuestionUsesLiveCatalogPath() {
        assertEquals(AgentIntentRouter.Intent.CATALOG, AgentIntentRouter.route("iphone 15"));
    }
}

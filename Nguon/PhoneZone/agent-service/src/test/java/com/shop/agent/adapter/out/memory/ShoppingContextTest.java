package com.shop.agent.adapter.out.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoppingContextTest {
    @Test
    void remembersStableConstraintsAcrossFollowUpQuestions() {
        var context = SessionMemoryTool.ShoppingContext.empty()
                .merge("Tìm điện thoại dưới 15 triệu RAM 8GB để chơi game")
                .merge("Máy nào phù hợp hơn?");

        assertEquals(15_000_000L, context.maxBudget());
        assertEquals(8, context.minRamGb());
        assertEquals("gaming", context.primaryNeed());
    }
}

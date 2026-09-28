package com.closeloop.ai.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextPlanTest {

    @Test
    void tokenEstimatorCjkVsAscii() {
        assertTrue(TokenEstimator.estimate("中文中文中文") > 0);
        assertTrue(TokenEstimator.estimate("abcd") >= 1);
        assertEquals(0, TokenEstimator.estimate(""));
        assertEquals(0, TokenEstimator.estimate(null));
    }

    @Test
    void truncatesLowPriorityWhenOverBudget() {
        ContextPlan plan = ContextPlan.ofTotal(50)
                .budget(ContextPlan.Slot.SYSTEM_CONTRACT, 1, 20)
                .budget(ContextPlan.Slot.KNOWLEDGE_CHUNKS, 5, 1000)
                .put(ContextPlan.Slot.SYSTEM_CONTRACT, "system contract rules")
                .put(ContextPlan.Slot.KNOWLEDGE_CHUNKS, "知识".repeat(200));
        ContextPlan.Assembled a = plan.assemble();
        assertTrue(a.usedTokens() <= 80);
        assertTrue(a.slots().containsKey(ContextPlan.Slot.SYSTEM_CONTRACT));
        assertTrue(a.droppedSlots().containsKey(ContextPlan.Slot.KNOWLEDGE_CHUNKS)
                || a.allocatedTokens().get(ContextPlan.Slot.KNOWLEDGE_CHUNKS) < 400);
    }

    @Test
    void digestRecordsUsage() {
        ContextPlan.Assembled a = ContextPlan.ofTotal(100)
                .budget(ContextPlan.Slot.TASK_INSTRUCTION, 1, 100)
                .put(ContextPlan.Slot.TASK_INSTRUCTION, "task")
                .assemble();
        assertTrue(a.digest().contains("used="));
    }
}

package com.closeloop.ai.context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上下文装配计划：按槽位优先级装填，超预算从低优先级裁剪。
 * 裁剪结果可审计（droppedSlots / digest），排障时能回答「模型当时看到了什么」。
 */
public final class ContextPlan {

    public enum Slot {
        SYSTEM_CONTRACT,
        TASK_INSTRUCTION,
        LEARNER_PROFILE,
        KNOWLEDGE_CHUNKS,
        RECENT_TRANSCRIPT,
        FEW_SHOT,
        RAW_DOC,
        OUTPUT_SCHEMA
    }

    public record SlotBudget(int priority, int maxTokens) {}

    private final Map<Slot, SlotBudget> budgets = new LinkedHashMap<>();
    private final Map<Slot, String> content = new LinkedHashMap<>();
    private final Map<Slot, Integer> allocated = new LinkedHashMap<>();
    private final Map<Slot, String> dropped = new LinkedHashMap<>();
    private int totalBudget;

    public static ContextPlan ofTotal(int totalBudget) {
        ContextPlan p = new ContextPlan();
        p.totalBudget = Math.max(16, totalBudget);
        return p;
    }

    public ContextPlan budget(Slot slot, int priority, int maxTokens) {
        budgets.put(slot, new SlotBudget(priority, maxTokens));
        return this;
    }

    public ContextPlan put(Slot slot, String text) {
        content.put(slot, text == null ? "" : text);
        return this;
    }

    /** 装配：按 priority 升序（数字小优先）装填，超预算裁剪文本并记录 */
    public Assembled assemble() {
        allocated.clear();
        dropped.clear();
        var order = budgets.entrySet().stream()
                .sorted((a, b) -> Integer.compare(a.getValue().priority(), b.getValue().priority()))
                .toList();
        int used = 0;
        Map<Slot, String> result = new LinkedHashMap<>();
        for (var e : order) {
            Slot slot = e.getKey();
            SlotBudget b = e.getValue();
            String text = content.getOrDefault(slot, "");
            if (text.isEmpty()) continue;
            int need = TokenEstimator.estimate(text);
            int room = Math.min(b.maxTokens(), totalBudget - used);
            if (room <= 0) {
                dropped.put(slot, "over_budget");
                continue;
            }
            if (need <= room) {
                result.put(slot, text);
                allocated.put(slot, need);
                used += need;
            } else {
                // 按字符比例裁剪到 room token（粗估）
                int keepChars = Math.max(0, (int) (text.length() * (room / (double) need)));
                String cut = text.substring(0, Math.min(text.length(), keepChars));
                int got = TokenEstimator.estimate(cut);
                // 估算误差导致仍超配额时二次硬裁
                while (got > room && !cut.isEmpty()) {
                    cut = cut.substring(0, Math.max(0, (int) (cut.length() * 0.9)));
                    got = TokenEstimator.estimate(cut);
                }
                result.put(slot, cut);
                allocated.put(slot, got);
                used += got;
                dropped.put(slot, "truncated need=" + need + " keep=" + got);
            }
        }
        return new Assembled(Map.copyOf(result), Map.copyOf(allocated), Map.copyOf(dropped), used, totalBudget);
    }

    public record Assembled(
            Map<Slot, String> slots,
            Map<Slot, Integer> allocatedTokens,
            Map<Slot, String> droppedSlots,
            int usedTokens,
            int totalBudget
    ) {
        /** 拼接非空槽位为最终 prompt 片段 */
        public String join(String separator) {
            StringBuilder sb = new StringBuilder();
            for (var e : slots.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                if (sb.length() > 0) sb.append(separator);
                sb.append(e.getValue());
            }
            return sb.toString();
        }

        public String digest() {
            StringBuilder sb = new StringBuilder();
            sb.append("used=").append(usedTokens).append('/').append(totalBudget);
            if (!droppedSlots.isEmpty()) sb.append(" dropped=").append(droppedSlots);
            return sb.toString();
        }
    }
}

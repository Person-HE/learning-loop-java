package com.closeloop.learning;

import com.closeloop.common.Dates;
import com.closeloop.state.model.Kp;

import java.util.Map;

/**
 * SM-2 + FSRS 双因子记忆调度（纯函数，零依赖，便于单测）。
 * 与 Node 版 store.js 的 sm2Update / retrievability / levelToQ 逐行为对齐。
 */
public final class Sm2 {

    private Sm2() {}

    /** 等级 → 复习质量 q（1~5） */
    public static int levelToQ(String level) {
        if (level == null) return 1;
        return switch (level) {
            case "优秀" -> 5;
            case "良好" -> 4;
            case "及格" -> 3;
            case "不及格" -> 2;
            default -> 1;
        };
    }

    /** 就地更新知识点记忆状态：ease 区间 [1.3, 2.8]，间隔序列 1 → 6 → interval×ease */
    public static void update(Kp.KpState s, int q) {
        double ease = Math.min(2.8, Math.max(1.3,
                round2(s.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))));
        int reviews = s.reviews + 1;
        int interval;
        if (q <= 2) interval = 1;
        else if (reviews <= 1) interval = 1;
        else if (reviews == 2) interval = 6;
        else interval = Math.max(2, (int) Math.round(s.interval * ease * (q == 3 ? 0.8 : 1)));
        Map<Integer, Double> coef = Map.of(5, 2.5, 4, 1.8, 3, 1.2, 2, 0.7, 1, 0.3);
        double c = coef.getOrDefault(q, 0.3);
        double stability = Math.round((s.stability > 0 ? s.stability * c : 1) * 10) / 10.0;
        s.status = interval >= 30 && q >= 4 ? "mastered" : q <= 2 ? "relearning" : "learning";
        s.ease = ease;
        s.interval = interval;
        s.reviews = reviews;
        s.stability = stability;
        s.due = Dates.todayStr(interval);
        s.lastReviewDate = Dates.todayStr();
    }

    /** 可提取性 R = e^(-Δt / S)，S 为记忆稳定性 */
    public static double retrievability(Kp.KpState s, String fromDate) {
        if (s.stability <= 0) return 1;
        String base = s.lastReviewDate != null && !s.lastReviewDate.isEmpty() ? s.lastReviewDate : s.due;
        int dt = Math.max(0, Dates.daysBetween(base, fromDate));
        return Math.exp(-dt / s.stability);
    }

    public static double retrievability(Kp.KpState s) {
        return retrievability(s, Dates.todayStr());
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

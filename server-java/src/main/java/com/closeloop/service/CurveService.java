package com.closeloop.service;

import com.closeloop.common.Dates;
import com.closeloop.common.Utils;
import com.closeloop.learning.Sm2;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Kp;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 遗忘曲线视图：单知识点（预测保持率 R=e^(−Δt/S) + 实测复习序列）与全局汇总（中位稳定性 / 平均 R / 到期清单）。
 */
@Service
public class CurveService {

    private final StateManager state;

    public CurveService(StateManager state) {
        this.state = state;
    }

    private static Map<String, Object> kpView(Kp k) {
        return Utils.m(
                "id", k.id, "title", k.title, "category", k.category, "domain", k.domain,
                "chapter", k.chapter, "difficulty", k.difficulty, "hot", k.hot, "path", k.path);
    }

    public Map<String, Object> curve(String kpId) {
        AppState st = state.state();
        Kp kp = st.kps.get(kpId);
        if (kp == null || "new".equals(kp.state.status)) {
            throw com.closeloop.common.ApiException.notFound("知识点不存在或未学习");
        }
        String today = Dates.todayStr();
        String lastDate = Utils.str(kp.state.lastReviewDate, Utils.str(kp.state.due, today));
        List<Map<String, Object>> actual = new ArrayList<>();
        for (Kp.HistoryEntry h : kp.state.history) {
            actual.add(Utils.m("date", h.date, "score", h.score, "stability", h.stability));
        }
        return Utils.m(
                "kp", kpView(kp),
                "state", kp.state,
                "actual", actual,
                "R", Sm2.retrievability(kp.state, today),
                "daysSince", Math.max(0, Dates.daysBetween(lastDate, today)),
                "today", today);
    }

    public Map<String, Object> summary() {
        AppState st = state.state();
        String today = Dates.todayStr();
        List<Kp> learned = st.kps.values().stream()
                .filter(k -> !"new".equals(k.state.status) && !k.state.history.isEmpty())
                .toList();
        List<Double> stabs = new ArrayList<>();
        for (Kp k : learned) if (k.state.stability > 0) stabs.add(k.state.stability);
        stabs.sort(Comparator.naturalOrder());
        double medianS = !stabs.isEmpty() ? stabs.get(stabs.size() / 2) : 0;
        double avgR = learned.isEmpty() ? 0 : learned.stream().mapToDouble(k -> Sm2.retrievability(k.state, today)).sum() / learned.size();
        List<Map<String, Object>> due = new ArrayList<>();
        for (Kp k : learned) {
            if (Dates.cmpDate(k.state.due, today) <= 0) {
                due.add(Utils.m("kp", kpView(k), "state", k.state, "R", Sm2.retrievability(k.state, today)));
            }
        }
        due.sort(Comparator.comparingDouble(a -> ((Number) a.get("R")).doubleValue()));
        return Utils.m("medianS", medianS, "avgR", avgR, "dueCount", due.size(), "due", due);
    }
}

package com.closeloop.learning;

import com.closeloop.common.Dates;
import com.closeloop.state.model.AnswerRecord;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** KPI（连续天数 / 已消灭缺口 / 口述率 / 累计作答）与三类掌握度统计（纯函数） */
public final class Kpi {

    private Kpi() {}

    public static Map<String, Object> calc(AppState st) {
        Set<String> done = new HashSet<>();
        if (st.sessionToday.count > 0) done.add(st.sessionToday.date);
        for (AppState.SessionDay s : st.sessions) if (s.count > 0) done.add(s.date);
        int streak = 0;
        while (done.contains(Dates.todayStr(-streak))) streak++;
        long resolvedGaps = st.gaps.stream().filter(g -> g.resolvedAt != null).count();
        int total = st.records.size();
        long spoken = st.records.stream().filter(r -> r.spoken).count();
        int spokenRate = total > 0 ? (int) Math.round(spoken * 100.0 / total) : 0;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("streak", streak);
        out.put("resolvedGaps", resolvedGaps);
        out.put("spokenRate", spokenRate);
        out.put("totalRecords", total);
        return out;
    }

    public static Map<String, Map<String, Integer>> categoryMastery(AppState st) {
        Map<String, Map<String, Integer>> cats = new LinkedHashMap<>();
        cats.put("java", new LinkedHashMap<>(Map.of("total", 0, "learned", 0, "mastered", 0)));
        cats.put("algo", new LinkedHashMap<>(Map.of("total", 0, "learned", 0, "mastered", 0)));
        cats.put("ai", new LinkedHashMap<>(Map.of("total", 0, "learned", 0, "mastered", 0)));
        for (Kp kp : st.kps.values()) {
            Map<String, Integer> c = cats.get(kp.category);
            if (c == null) continue;
            c.merge("total", 1, Integer::sum);
            if (!"new".equals(kp.state.status)) c.merge("learned", 1, Integer::sum);
            if ("mastered".equals(kp.state.status)) c.merge("mastered", 1, Integer::sum);
        }
        return cats;
    }

    /** 缺口消灭门槛：必须该知识点最近一次作答 ≥75 分（防自欺），拒绝手动拍板 */
    public static Gap resolveGap(AppState st, String gapId) {
        Gap g = st.gaps.stream().filter(x -> x.id.equals(gapId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("缺口不存在"));
        if (g.resolvedAt != null) throw new IllegalArgumentException("该缺口已解决");
        Kp kp = st.kps.get(g.kpId);
        Integer lastScore = kp == null ? null : kp.state.lastScore;
        if (lastScore == null || lastScore < 75) {
            throw new IllegalArgumentException("缺口必须在该知识点最近一次回答 ≥75 分时才能标记解决（防自欺）");
        }
        g.resolvedAt = Dates.todayStr();
        return g;
    }

    /** 记录引用（供 markStall 等使用） */
    public static AnswerRecord findRecord(AppState st, String recordId) {
        return st.records.stream().filter(x -> x.id.equals(recordId)).findFirst().orElse(null);
    }
}

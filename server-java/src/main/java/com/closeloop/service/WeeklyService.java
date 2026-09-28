package com.closeloop.service;

import com.closeloop.ai.AiCall;
import com.closeloop.ai.AiClient;
import com.closeloop.ai.FormatReject;
import com.closeloop.ai.prompt.ProjectPrompts;
import com.closeloop.common.ApiException;
import com.closeloop.common.Dates;
import com.closeloop.common.Utils;
import com.closeloop.learning.Kpi;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.AppState;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** AI 教练周报：聚合 KPI + 三类掌握度 + 最近 7 天会话 + 最近缺口 → AI 生成 strengths/weaknesses/focus */
@Service
public class WeeklyService {

    private final AiClient ai;
    private final StateManager state;

    public WeeklyService(AiClient ai, StateManager state) {
        this.ai = ai;
        this.state = state;
    }

    public Map<String, Object> weekly() {
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");
        AppState st = state.state();
        Map<String, Map<String, Integer>> mastery = Kpi.categoryMastery(st);
        Map<String, Object> kpi = Kpi.calc(st);

        List<Object[]> recentSessions = new ArrayList<>();
        for (AppState.SessionDay s : st.sessions) recentSessions.add(new Object[]{s.date, s.count, s.avg});
        if (st.sessionToday.count > 0) {
            recentSessions.add(new Object[]{st.sessionToday.date, st.sessionToday.count, st.sessionToday.avg});
        }
        if (recentSessions.size() > 7) {
            recentSessions = recentSessions.subList(recentSessions.size() - 7, recentSessions.size());
        }
        StringBuilder rs = new StringBuilder();
        for (Object[] s : recentSessions) {
            if (rs.length() > 0) rs.append('、');
            rs.append(s[0]).append('(').append(s[1]).append("题/均分").append(s[2]).append(')');
        }
        StringBuilder rg = new StringBuilder();
        int from = Math.max(0, st.gaps.size() - 10);
        for (int i = from; i < st.gaps.size(); i++) {
            var g = st.gaps.get(i);
            if (rg.length() > 0) rg.append('、');
            rg.append(g.title).append('（').append(g.label).append(g.resolvedAt != null ? "·已解决" : "").append('）');
        }
        Map<String, Integer> mj = mastery.get("java"), ma = mastery.get("algo"), mi = mastery.get("ai");
        String summaryText = String.join("\n",
                "统计日期：" + Dates.todayStr() + "｜连续学习 " + kpi.get("streak") + " 天｜累计作答 " + kpi.get("totalRecords")
                        + " 题｜口述率 " + kpi.get("spokenRate") + "%｜已消灭缺口 " + kpi.get("resolvedGaps"),
                "三类掌握度：Java " + mj.get("learned") + "/" + mj.get("total") + "（掌握" + mj.get("mastered") + "）｜算法 "
                        + ma.get("learned") + "/" + ma.get("total") + "（掌握" + ma.get("mastered") + "）｜AI "
                        + mi.get("learned") + "/" + mi.get("total") + "（掌握" + mi.get("mastered") + "）",
                "最近 7 天会话：" + (rs.length() > 0 ? rs : "无"),
                "最近缺口：" + (rg.length() > 0 ? rg : "无"));

        // 上游网关长尾实锤（审计账本同尺寸 8.9s vs 122.7s）：单次截止倒逼全局重试，不再陪跑挂死的请求。
        // 截止时长可配置（app.ai.weekly-timeout-seconds，默认 60s），便于低阈值合成复测重试链。
        JsonNode report = ai.chatJson(new AiCall(ProjectPrompts.weekly(summaryText), 0.5, 1500, "weekly")
                .withTimeoutSeconds(ai.props().weeklyTimeoutSeconds()), d -> {
            if (!d.path("strengths").isArray() || !d.path("weaknesses").isArray()) {
                throw new FormatReject("周报结构不完整");
            }
        });
        return Utils.m("report", Utils.m(
                "summary", Utils.cut(report.path("summary").asText(""), 500),
                "strengths", QuestionService.strList(report.path("strengths"), 3),
                "weaknesses", QuestionService.strList(report.path("weaknesses"), 3),
                "focus", QuestionService.strList(report.path("focus"), 4)));
    }
}

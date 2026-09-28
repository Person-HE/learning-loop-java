package com.closeloop.service;

import com.closeloop.ai.AiCall;
import com.closeloop.ai.AiClient;
import com.closeloop.ai.FormatReject;
import com.closeloop.ai.prompt.MockPrompts;
import com.closeloop.common.ApiException;
import com.closeloop.common.Dates;
import com.closeloop.common.Utils;
import com.closeloop.project.ResumeData;
import com.closeloop.project.ResumeData.Facet;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.ProjectState;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模拟真人面试官（对话式项目拷打）：开场即第 1 问 → 每轮即时反馈 + 动态追问 → 结束总结回写切面状态。
 * 会话存 st.project.mock（{facetId, history, qCount, maxQuestions, startedAt}），追问不消耗主问题数。
 */
@Service
public class MockInterviewService {

    public static final int MOCK_MAX_QUESTIONS = 5;

    private static final Facet DEFAULT_FACET = new Facet("f-all", "项目整体", "面试官从简历中自主选择切入点", "");
    private static final Facet FALLBACK_FACET = new Facet("f-all", "项目整体", "面试官自由切入", "");

    private final AiClient ai;
    private final StateManager state;

    public MockInterviewService(AiClient ai, StateManager state) {
        this.ai = ai;
        this.state = state;
    }

    /** 开启面试：facetId 可选（不传则面试官自由从简历选切入点） */
    public Map<String, Object> start(String facetId) {
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");
        Facet facet = facetId == null || facetId.isEmpty() ? DEFAULT_FACET : ResumeData.facetById(facetId);
        JsonNode data = ai.chatJson(new AiCall(MockPrompts.open(facet, ResumeData.buildResumeText()), 0.7, 800, "mock-open"), d -> {
            if (d.path("speech").asText("").trim().isEmpty()) throw new FormatReject("AI 未生成开场白");
        });
        String speech = data.path("speech").asText("").trim();
        state.mutateVoid(() -> {
            ProjectState.Mock mock = new ProjectState.Mock();
            mock.facetId = facet.id();
            mock.history = new ArrayList<>();
            mock.history.add(Utils.m("role", "interviewer", "content", speech));
            mock.qCount = 1;
            mock.maxQuestions = MOCK_MAX_QUESTIONS;
            mock.startedAt = System.currentTimeMillis();
            state.state().project.mock = mock;
        });
        return Utils.m("facetId", facet.id(), "speech", speech, "qCount", 1, "maxQuestions", MOCK_MAX_QUESTIONS);
    }

    /** 每轮：候选人回答 → 面试官即时反馈 + 追问/下一问（或 finished） */
    public Map<String, Object> turn(String answer) {
        ProjectState.Mock mock = state.state().project.mock;
        if (mock == null) throw ApiException.notFound("没有进行中的模拟面试，请先开始");
        if (answer == null || answer.trim().length() < 4) throw ApiException.badRequest("回答太短");
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");
        Facet facet = ResumeData.facets().stream().filter(f -> f.id().equals(mock.facetId)).findFirst().orElse(FALLBACK_FACET);

        mock.history.add(Utils.m("role", "candidate", "content", Utils.cut(answer, 3000)));
        JsonNode data = ai.chatJson(new AiCall(MockPrompts.turn(facet, ResumeData.buildResumeText(),
                mock.history, mock.qCount, mock.maxQuestions), 0.7, 900, "mock-turn"), d -> {
            if (!d.path("finished").asBoolean(false) && d.path("question").asText("").trim().isEmpty()) {
                throw new FormatReject("AI 未生成下一问");
            }
        });
        boolean finished = data.path("finished").asBoolean(false);
        String feedback = data.path("feedback").asText("").trim();
        String nextQ = data.path("question").asText("").trim();
        String levelRaw = data.path("level").asText("");
        String level = levelRaw.matches("^L[0-4]$") ? levelRaw : null;

        return state.mutate(() -> {
            ProjectState.Mock m = state.state().project.mock;
            String interviewerText = feedback.isEmpty() ? nextQ : feedback + (nextQ.isEmpty() ? "" : " " + nextQ);
            m.history.add(Utils.m("role", "interviewer", "content", interviewerText));
            // 追问不消耗主问题数；只有换到下一个技术点的新主问题才 +1
            m.qCount = finished ? m.maxQuestions : Math.min(m.maxQuestions, m.qCount + 1);
            return Utils.m(
                    "feedback", feedback,
                    "question", nextQ,
                    "finished", finished,
                    "qCount", m.qCount,
                    "maxQuestions", m.maxQuestions,
                    "level", level);
        });
    }

    /** 结束面试：AI 总结评估 → 写入项目会话（kind=mock）+ 更新切面调度 → 清空进行时会话 */
    public Map<String, Object> end() {
        ProjectState.Mock mock = state.state().project.mock;
        if (mock == null) throw ApiException.notFound("没有进行中的模拟面试");
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");
        Facet facet = ResumeData.facets().stream().filter(f -> f.id().equals(mock.facetId)).findFirst().orElse(FALLBACK_FACET);

        JsonNode outcome = ai.chatJson(new AiCall(MockPrompts.summary(facet, ResumeData.buildResumeText(),
                mock.history, mock.qCount), 0.4, 2500, "mock-end"), d -> {
            if (!d.path("total100").isNumber() && !d.path("total10").isNumber()) throw new FormatReject("总结缺少总分");
        });
        Map<String, Object> norm = ProjectInterviewService.normalizeInterviewScore(outcome, false);
        // finalWords 置于首位（与 Node 版键序一致）
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("finalWords", Utils.cut(outcome.path("finalWords").asText(""), 500));
        result.putAll(norm);

        final int rounds = mock.history.size();
        ProjectState.FacetState facetState = state.mutate(() -> {
            ProjectState p = state.state().project;
            Map<String, Object> session = Utils.m(
                    "id", "MS-" + System.currentTimeMillis(),
                    "date", Dates.todayStr(),
                    "facetId", facet.id(),
                    "facetName", facet.name(),
                    "kind", "mock",
                    "rounds", rounds,
                    "total100", result.get("total100"),
                    "level", result.get("level"),
                    "gaps", result.get("gaps"));
            ProjectInterviewService.recordFacetResult(p, facet, result, session);
            p.mock = null;
            return p.facets.get(facet.id());
        });
        return Utils.m("result", result, "facetState", facetState);
    }
}

package com.closeloop.service;

import com.closeloop.ai.AiCall;
import com.closeloop.ai.AiClient;
import com.closeloop.ai.FormatReject;
import com.closeloop.ai.prompt.KbPrompts;
import com.closeloop.common.ApiException;
import com.closeloop.common.Utils;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.AnswerRecord;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.QueueItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 学习舱「合书复述」评分：只数关键点覆盖 M/N + 列漏点提示，不泄露完整标准答案（防假性学习）。
 * 复述不消耗题目、不回写 SM-2 —— 它是读后自检，不是面试作答。
 */
@Service
public class RestateService {

    private final AiClient ai;
    private final StateManager state;

    public RestateService(AiClient ai, StateManager state) {
        this.ai = ai;
        this.state = state;
    }

    public Map<String, Object> score(String kpId, String questionId, String answerText) {
        if (kpId == null || kpId.isEmpty() || questionId == null || questionId.isEmpty()) {
            throw ApiException.badRequest("参数缺失");
        }
        Kp kp = state.state().kps.get(kpId);
        if (kp == null) throw ApiException.notFound("知识点不存在");
        String answer = Utils.str(answerText, "");
        if (answer.trim().length() < 10) throw ApiException.badRequest("复述内容太短，至少写一句话（≥10字）");
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key");

        // 找题：队列 → 直接出题会话 → 历史记录（支持答过的题复述）
        AppState st = state.state();
        String question = null;
        List<String> answerPoints = new ArrayList<>();
        outer:
        for (QueueItem it : st.queue) {
            if (kpId.equals(it.kpId)) {
                for (QueueItem.Question x : it.questions) {
                    if (questionId.equals(x.id)) {
                        question = x.question;
                        answerPoints = x.answerPoints;
                        break outer;
                    }
                }
            }
        }
        AppState.GenSession gs = st.genSessions.get(kpId);
        if (question == null && gs != null) {
            for (QueueItem.Question x : gs.questions) {
                if (questionId.equals(x.id)) {
                    question = x.question;
                    answerPoints = x.answerPoints;
                    break;
                }
            }
        }
        if (question == null) {
            List<AnswerRecord> recs = st.records;
            for (int i = recs.size() - 1; i >= 0; i--) {
                AnswerRecord r = recs.get(i);
                if (kpId.equals(r.kpId) && questionId.equals(r.questionId)) {
                    question = r.question;
                    answerPoints = r.answerPoints == null ? List.of() : r.answerPoints;
                    break;
                }
            }
        }
        if (question == null) throw ApiException.notFound("题目不存在，请重新出题");

        JsonNode data = ai.chatJson(new AiCall(KbPrompts.restate(question, answerPoints, com.closeloop.ai.govern.Untrusted.answer(answer)), 0.3, 2000, "restate-score"), d -> {
            if (!d.path("coverage_count").isNumber()) throw new FormatReject("复述评估结果缺少 coverage_count");
        });

        List<Map<String, Object>> covered = new ArrayList<>();
        JsonNode cov = data.path("covered");
        if (cov.isArray()) {
            int n = Math.min(8, cov.size());
            for (int i = 0; i < n; i++) {
                JsonNode x = cov.get(i);
                covered.add(x.isTextual()
                        ? Utils.m("point", x.asText(), "note", "")
                        : Utils.m("point", x.path("point").asText(""), "note", x.path("note").asText("")));
            }
        }
        List<Map<String, Object>> missed = new ArrayList<>();
        JsonNode mis = data.path("missed");
        if (mis.isArray()) {
            int n = Math.min(8, mis.size());
            for (int i = 0; i < n; i++) {
                JsonNode m = mis.get(i);
                missed.add(Utils.m(
                        "point", Utils.cut(m.path("point").asText(""), 200),
                        "hint", Utils.cut(m.path("hint").asText(""), 60)));
            }
        }
        return Utils.m(
                "covered", covered,
                "missed", missed,
                "coverage_count", (int) Math.round(data.path("coverage_count").asDouble(0)),
                "total_count", (int) Math.max(1, Math.round(data.path("total_count").asDouble(1))),
                "coverage_pct", (int) Math.round(data.path("coverage_pct").asDouble(0)),
                "comment", data.path("comment").asText(""));
    }
}

package com.closeloop.service;

import com.closeloop.ai.AiCall;
import com.closeloop.ai.AiClient;
import com.closeloop.ai.FormatReject;
import com.closeloop.ai.prompt.KbPrompts;
import com.closeloop.common.ApiException;
import com.closeloop.common.Utils;
import com.closeloop.config.AppProperties;
import com.closeloop.knowledge.KbService;
import com.closeloop.learning.Kpi;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.AnswerRecord;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.QueueItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 缺口生命周期：列表 / 检测题生成（专门考未解决缺口，一次性题目存 gapTests，答 ≥75 分由评分路由自动消灭）/
 * 手动消灭（硬性门槛：该知识点最近一次 ≥75 分，防自欺）。
 */
@Service
public class GapService {

    private final AiClient ai;
    private final KbService kb;
    private final AppProperties props;
    private final StateManager state;

    public GapService(AiClient ai, KbService kb, AppProperties props, StateManager state) {
        this.ai = ai;
        this.kb = kb;
        this.props = props;
        this.state = state;
    }

    public List<Gap> list() {
        return state.state().gaps;
    }

    /** 生成缺口检测题：返回独立队列项 {item}（不进今日队列，前端直接开答） */
    public Map<String, Object> generateTest(String gapId) {
        AppState st0 = state.state();
        Gap g = st0.gaps.stream().filter(x -> gapId.equals(x.id)).findFirst().orElse(null);
        if (g == null) throw ApiException.notFound("缺口不存在");
        if (g.resolvedAt != null) throw ApiException.badRequest("该缺口已消灭，无需检测");
        Kp kp = st0.kps.get(g.kpId);
        if (kp == null) throw ApiException.notFound("知识点不存在");
        String doc = kb.readDoc(kp);
        if (doc == null || doc.isEmpty()) throw ApiException.notFound("找不到知识库文档：" + kp.path);
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key");

        // 暴露该缺口的原题：该 kp 最近一次含同标签缺口的答题记录
        AnswerRecord rec = null;
        for (int i = st0.records.size() - 1; i >= 0; i--) {
            AnswerRecord r = st0.records.get(i);
            if (!g.kpId.equals(r.kpId)) continue;
            boolean hit = r.gaps.stream().anyMatch(x -> g.label.equals(Utils.str(x.get("label"), "")));
            if (hit) { rec = r; break; }
        }
        final String docText = Utils.cut(doc, props.kbTextLimit());
        final AnswerRecord recRef = rec;

        JsonNode data = state.withGenLock(() -> ai.chatJson(
                new AiCall(KbPrompts.gapTest(kp, g, docText,
                        recRef != null ? recRef.question : "",
                        recRef != null && recRef.answerPoints != null ? recRef.answerPoints : List.of()),
                        0.5, 1500, "gap-test"),
                d -> {
                    if (d.path("question").asText("").trim().isEmpty()) throw new FormatReject("检测题缺少 question");
                }));

        return state.mutate(() -> {
            AppState st = state.state();
            Gap gp = st.gaps.stream().filter(x -> gapId.equals(x.id)).findFirst().orElseThrow(() -> ApiException.notFound("缺口不存在"));
            QueueItem.Question q = new QueueItem.Question();
            q.id = "GQ-" + gp.id + "-" + System.currentTimeMillis();
            q.kpId = gp.kpId;
            q.type = Utils.cut(Utils.str(data.path("type").asText("T1"), "T1"), 2);
            q.difficulty = Utils.clamp(Utils.parseIntOr(data.path("difficulty").asText(""), kp.difficulty), 1, 5);
            q.question = data.path("question").asText("").trim();
            q.answerPoints = QuestionService.strList(data.path("answerPoints"), 6);
            q.anchor = data.path("anchor").asText("").trim();
            String point = data.path("point").asText("").trim();
            q.point = point.isEmpty() ? gp.label : point;
            q.gapId = gp.id;
            st.gapTests.put(q.id, q);
            return Utils.m("item", Utils.m(
                    "kpId", gp.kpId,
                    "kind", "gaptest",
                    "reason", "缺口检测：" + gp.label,
                    "gapId", gp.id,
                    "qIdx", 0,
                    "questions", List.of(q),
                    "genError", null));
        });
    }

    /** 手动消灭缺口（≥75 分门槛校验） */
    public Map<String, Object> resolve(String gapId) {
        return state.mutate(() -> {
            AppState st = state.state();
            Gap g;
            try {
                g = Kpi.resolveGap(st, gapId);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest(String.valueOf(e.getMessage()));
            }
            return Utils.m("ok", true, "gap", g, "kpi", Kpi.calc(st));
        });
    }
}

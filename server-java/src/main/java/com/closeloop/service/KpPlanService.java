package com.closeloop.service;

import com.closeloop.ai.AiCall;
import com.closeloop.ai.AiClient;
import com.closeloop.ai.FormatReject;
import com.closeloop.ai.prompt.KbPrompts;
import com.closeloop.common.ApiException;
import com.closeloop.common.Utils;
import com.closeloop.config.AppProperties;
import com.closeloop.knowledge.KbService;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.AnswerRecord;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Kp;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学习舱「考点地图 + 记忆卡」：AI 懒生成，缓存于 kp.state.kpPlan；
 * resolvedPoints 由历史答题记录聚合（考到该考点且 ≥75 分 → 已掌握）。
 */
@Service
public class KpPlanService {

    private final AiClient ai;
    private final KbService kb;
    private final AppProperties props;
    private final StateManager state;

    public KpPlanService(AiClient ai, KbService kb, AppProperties props, StateManager state) {
        this.ai = ai;
        this.kb = kb;
        this.props = props;
        this.state = state;
    }

    public Kp.KpPlan points(String kpId, boolean fresh) {
        Kp kp = state.state().kps.get(kpId);
        if (kp == null) throw ApiException.notFound("知识点不存在");
        if (kp.state.kpPlan != null && !fresh) return kp.state.kpPlan;

        String doc = Utils.str(kb.readDoc(kp), "");
        if (doc.isEmpty()) throw ApiException.notFound("找不到知识库文档：" + kp.path);
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key");

        JsonNode data = ai.chatJson(new AiCall(KbPrompts.kpPlan(Utils.cut(doc, props.kbTextLimit()), kp.id, kp.title), 0.3, 2500, "kp-points"), d -> {
            if (!d.path("points").isArray()) throw new FormatReject("考点清单生成失败");
        });

        Kp.KpPlan plan = new Kp.KpPlan();
        List<String> pointNames = new ArrayList<>();
        JsonNode points = data.path("points");
        if (points.isArray()) {
            int n = Math.min(6, points.size());
            for (int i = 0; i < n; i++) {
                JsonNode p = points.get(i);
                String name = Utils.cut(p.path("name").asText(""), 20);
                pointNames.add(name);
                plan.points.add(Utils.m(
                        "name", name,
                        "summary", Utils.cut(p.path("summary").asText(""), 200),
                        "anchor", Utils.cut(p.path("anchor").asText(""), 100)));
            }
        }
        JsonNode cards = data.path("memory_cards");
        if (cards.isArray()) {
            int n = Math.min(5, cards.size());
            for (int i = 0; i < n; i++) {
                JsonNode c = cards.get(i);
                plan.memory_cards.add(Utils.m(
                        "core", Utils.cut(c.path("core").asText(""), 200),
                        "detail", Utils.cut(c.path("detail").asText(""), 200)));
            }
        }
        // 聚合已掌握考点：历史 ≥75 分且 point 命中考点名
        AppState st = state.state();
        Set<String> seen = new LinkedHashSet<>();
        for (AnswerRecord r : st.records) {
            if (!kpId.equals(r.kpId) || r.totalScore < 75) continue;
            String pName = Utils.str(r.point, "");
            if (!pName.isEmpty() && pointNames.contains(pName)) seen.add(pName);
        }
        plan.resolvedPoints = new ArrayList<>(seen);

        state.mutateVoid(() -> {
            Kp k = state.state().kps.get(kpId);
            if (k != null) {
                k.state.kpPlan = plan;
                // mysql 模式 save() 只写 blob，kpPlan 存 kp_state.plan_json，必须热写该行
                state.saveHot(k, null, null);
            }
        });
        return plan;
    }
}

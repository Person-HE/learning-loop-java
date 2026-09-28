package com.closeloop.service;

import com.closeloop.ai.AiCall;
import com.closeloop.ai.AiClient;
import com.closeloop.ai.prompt.KbPrompts;
import com.closeloop.ai.prompt.LcPrompts;
import com.closeloop.common.ApiException;
import com.closeloop.common.Utils;
import com.closeloop.config.AppProperties;
import com.closeloop.knowledge.KbService;
import com.closeloop.knowledge.LcDataService;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.QueueItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 出题服务：知识库知识点（5 题、必含 T2/T3、算法类含 T6 手写题）与 LC 知识点（复习深挖 2 题）。
 * 只依赖 ai/knowledge/state 三个下层模块，被队列组装与直接出题路由复用。
 */
@Service
public class QuestionService {

    private final AiClient ai;
    private final KbService kb;
    private final LcDataService lc;
    private final AppProperties props;
    private final StateManager state;

    public QuestionService(AiClient ai, KbService kb, LcDataService lc, AppProperties props, StateManager state) {
        this.ai = ai;
        this.kb = kb;
        this.lc = lc;
        this.props = props;
        this.state = state;
    }

    /** 生成某知识点的题目（不触碰队列；LC 知识点自动走 LC 复习出题） */
    public List<QueueItem.Question> genForKp(Kp kp) {
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");
        return kp.isLc ? genForLcKp(kp) : genForKbKp(kp);
    }

    /** 力扣知识点出题：概念深挖 + 变体手写 */
    private List<QueueItem.Question> genForLcKp(Kp kp) {
        Object no = kp.lc == null ? null : kp.lc.get("no");
        LcDataService.LcProblem prob = lc.byNo(String.valueOf(no));
        if (prob == null) throw ApiException.internal("LC 题目数据缺失：" + kp.id);
        String learner = "首次复习";
        List<Map.Entry<String, Kp.QuestionHist>> entries = new ArrayList<>();
        if (kp.state.questionHistory != null) {
            for (Map.Entry<String, Kp.QuestionHist> e : kp.state.questionHistory.entrySet()) {
                if (e.getValue() != null && e.getValue().score >= 0) entries.add(e);
            }
        }
        if (!entries.isEmpty()) {
            Kp.QuestionHist h = entries.get(0).getValue();
            String q = h.question == null ? "该题" : h.question.substring(0, Math.min(60, h.question.length()));
            learner = "上次答「" + q + "」得 " + h.score + " 分" + (h.score < 80 ? "（未掌握，需针对性补强）" : "");
        }
        AiCall call = new AiCall(LcPrompts.review(prob, learner), 0.6, 2500, "lc-gen");
        JsonNode data = ai.chatJson(call, d -> {
            if (!d.isArray()) throw new com.closeloop.ai.FormatReject("LC 复习出题结果不是数组");
            for (JsonNode x : d) {
                if (x.path("question").asText("").trim().isEmpty()) throw new com.closeloop.ai.FormatReject("题目缺少 question");
                if (x.path("point").asText("").trim().isEmpty()) throw new com.closeloop.ai.FormatReject("题目缺少 point 考点");
            }
        });
        long ts = System.currentTimeMillis();
        List<QueueItem.Question> qs = new ArrayList<>();
        int n = Math.min(2, data.size());
        for (int i = 0; i < n; i++) {
            JsonNode q = data.get(i);
            QueueItem.Question x = new QueueItem.Question();
            x.id = "QLC-" + kp.id + "-" + (i + 1) + "-" + ts;
            x.kpId = kp.id;
            x.type = Utils.cut(Utils.str(q.path("type").asText("T6"), "T6"), 2);
            x.difficulty = Utils.clamp(Utils.parseIntOr(q.path("difficulty").asText(""), 3), 1, 5);
            x.question = q.path("question").asText("").trim();
            x.answerPoints = strList(q.path("answerPoints"), 6);
            String anchor = q.path("anchor").asText("").trim();
            x.anchor = !anchor.isEmpty() ? anchor : "lc-" + prob.no;
            x.point = q.path("point").asText("").trim();
            qs.add(x);
        }
        qs.removeIf(q -> q.question.length() < 6);
        if (qs.isEmpty()) throw ApiException.internal("AI 未生成有效题目，请重试");
        return qs;
    }

    /** 知识库知识点出题：文档 + 学习者画像 + 锚点 → 5 道八股 */
    private List<QueueItem.Question> genForKbKp(Kp kp) {
        String doc = kb.readDoc(kp);
        if (doc == null) throw ApiException.internal("找不到知识库文档：" + kp.path);
        String docText = Utils.cut(doc, props.kbTextLimit());
        AppState st = state.state();
        Gap openGap = st.gaps.stream().filter(g -> g.kpId.equals(kp.id) && g.resolvedAt == null).findFirst().orElse(null);
        KbPrompts.Learner learner = new KbPrompts.Learner(
                kp.state.lastScore,
                openGap != null ? openGap.label + "：" + openGap.detail : null,
                kp.state.lapses);
        AiCall call = new AiCall(KbPrompts.gen(kp, docText, learner), 0.7, 4000, "kb-gen");
        JsonNode data = ai.chatJson(call, d -> {
            if (!d.isArray()) throw new com.closeloop.ai.FormatReject("出题结果不是数组");
            if (d.size() < 4) throw new com.closeloop.ai.FormatReject("题目数量不足：要求 5 道，实际 " + d.size() + " 道，请补全到 5 道");
            for (JsonNode x : d) {
                if (x.path("question").asText("").trim().isEmpty()) throw new com.closeloop.ai.FormatReject("题目缺少 question");
                if (x.path("point").asText("").trim().isEmpty()) throw new com.closeloop.ai.FormatReject("题目缺少 point 考点，请为每道题提炼 ≤12 字考点名");
            }
        });
        long ts = System.currentTimeMillis();
        List<QueueItem.Question> qs = new ArrayList<>();
        int n = Math.min(5, data.size());
        for (int i = 0; i < n; i++) {
            JsonNode q = data.get(i);
            QueueItem.Question x = new QueueItem.Question();
            x.id = "Q-" + kp.id + "-" + (i + 1) + "-" + ts;
            x.kpId = kp.id;
            x.type = Utils.cut(Utils.str(q.path("type").asText("T2"), "T2"), 2);
            x.difficulty = Utils.clamp(Utils.parseIntOr(q.path("difficulty").asText(""), kp.difficulty), 1, 5);
            x.question = q.path("question").asText("").trim();
            x.answerPoints = strList(q.path("answerPoints"), 6);
            x.anchor = q.path("anchor").asText("").trim();
            x.point = q.path("point").asText("").trim();
            qs.add(x);
        }
        qs.removeIf(q -> q.question.length() < 6);
        if (qs.isEmpty()) throw ApiException.internal("AI 未生成有效题目，请重试");
        return qs;
    }

    /** 生成队列项题目并写回队列 */
    public List<QueueItem.Question> prepareItemQuestions(String kpId) {
        Kp kp = state.state().kps.get(kpId);
        if (kp == null) throw ApiException.notFound("知识点不存在");
        List<QueueItem.Question> qs = genForKp(kp);
        state.mutateVoid(() -> {
            QueueItem it = state.state().queue.stream().filter(x -> x.kpId.equals(kpId)).findFirst().orElse(null);
            if (it != null) {
                it.questions = qs;
                it.genError = null;
            }
        });
        return qs;
    }

    /**
     * 单知识点直接出题（地图/曲线「开始挑战」）：生成 → 若已在队列则写回 → 登记 genSessions
     * （每题只允许评分一次，24h 过期清理），返回不含 answerPoints 的公共题目。
     */
    public Map<String, Object> genDirect(String kpId) {
        Kp kp = state.state().kps.get(kpId);
        if (kp == null) throw ApiException.notFound("知识点不存在");
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");
        List<QueueItem.Question> qs = state.withGenLock(() -> genForKp(kp));
        return state.mutate(() -> {
            AppState st = state.state();
            QueueItem it = st.queue.stream().filter(x -> kpId.equals(x.kpId)).findFirst().orElse(null);
            if (it != null) {
                it.questions = qs;
                it.genError = null;
            }
            long nowTs = System.currentTimeMillis();
            st.genSessions.entrySet().removeIf(e -> nowTs - e.getValue().ts > 24L * 3600 * 1000);
            AppState.GenSession session = new AppState.GenSession();
            session.questions = qs;
            session.ts = nowTs;
            st.genSessions.put(kpId, session);
            return Utils.m("kpId", kpId, "questions", KbPrompts.publicQuestions(qs));
        });
    }

    static List<String> strList(JsonNode node, int limit) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode x : node) {
                if (out.size() >= limit) break;
                out.add(x.isTextual() ? x.asText() : x.toString());
            }
        }
        return out;
    }
}

package com.closeloop.service;

import com.closeloop.ai.AiCall;
import com.closeloop.ai.context.ContextPlan;
import com.closeloop.ai.AiClient;
import com.closeloop.ai.prompt.Dims;
import com.closeloop.ai.prompt.KbPrompts;
import com.closeloop.ai.prompt.LcPrompts;
import com.closeloop.common.ApiException;
import com.closeloop.common.Dates;
import com.closeloop.common.Utils;
import com.closeloop.config.AppProperties;
import com.closeloop.knowledge.KbService;
import com.closeloop.knowledge.LcDataService;
import com.closeloop.learning.AnswerApplier;
import com.closeloop.learning.Kpi;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.AnswerRecord;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.LcExplain;
import com.closeloop.state.model.QueueItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 评分服务（answer/score 编排）：取题（队列 → 直接出题会话 → LC 手搓 → 缺口检测题）→
 * AI 面试官诊断 → 规范化（snake 契约键）→ 学习闭环回写（SM-2/缺口/队列移除）。
 */
@Service
public class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    private static final Pattern LC_CODE_ID = Pattern.compile("^LC-(\\d+)$");

    private final AiClient ai;
    private final KbService kb;
    private final LcDataService lc;
    private final AppProperties props;
    private final StateManager state;
    private final com.closeloop.application.rag.RagService rag;

    public GradingService(AiClient ai, KbService kb, LcDataService lc, AppProperties props, StateManager state,
                          com.closeloop.application.rag.RagService rag) {
        this.ai = ai;
        this.kb = kb;
        this.lc = lc;
        this.props = props;
        this.state = state;
        this.rag = rag;
    }

    /**
     * 提交答案并评分。返回 {result, recordId, kpState, session, kpi}（与 Node 版响应一致）。
     */
    public Map<String, Object> score(String kpId, String questionId, String answerText,
                                     String code, String think, boolean spoken, Object stallMark) {
        if (kpId == null || kpId.isEmpty() || questionId == null || questionId.isEmpty()) {
            throw ApiException.badRequest("参数缺失");
        }
        AppState st0 = state.state();
        Kp kp = st0.kps.get(kpId);
        if (kp == null) throw ApiException.notFound("知识点不存在");
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");

        // ---------- 取题（四来源，一次性题源在此步内消费） ----------
        Take take = takeQuestion(kpId, questionId);
        QueueItem.Question q = take.question;
        if (q == null) throw ApiException.notFound("题目不存在或已过期，请重新出题");

        String doc = Utils.str(kb.readDoc(kp), "");
        // RAG：相关块优先注入；失败回退整篇截断（问题：单篇 6000 字硬切丢跨章因果）
        StringBuilder ragBlocks = new StringBuilder();
        try {
            String query = q.question + " " + Utils.str(q.point, "");
            for (com.closeloop.application.rag.RagService.Hit h : rag.search(query, 6)) {
                ragBlocks.append("【").append(h.docId()).append('#').append(h.heading()).append("】\n")
                        .append(h.text()).append("\n\n");
            }
        } catch (Exception e) {
            log.warn("[rag] 检索失败，回退 RAW_DOC: {}", e.getMessage());
        }
        ContextPlan.Assembled ctx = ContextPlan.ofTotal(Math.max(800, props.kbTextLimit() / 2))
                .budget(ContextPlan.Slot.KNOWLEDGE_CHUNKS, 2, props.kbTextLimit())
                .budget(ContextPlan.Slot.RAW_DOC, 3, props.kbTextLimit())
                .budget(ContextPlan.Slot.TASK_INSTRUCTION, 1, 400)
                .put(ContextPlan.Slot.KNOWLEDGE_CHUNKS, ragBlocks.toString())
                .put(ContextPlan.Slot.RAW_DOC, doc)
                .put(ContextPlan.Slot.TASK_INSTRUCTION, "按面试官诊断合同评分")
                .assemble();
        String docText = ctx.slots().getOrDefault(ContextPlan.Slot.KNOWLEDGE_CHUNKS, "");
        if (docText.isEmpty()) {
            docText = ctx.slots().getOrDefault(ContextPlan.Slot.RAW_DOC, Utils.cut(doc, props.kbTextLimit()));
        }
        log.debug("[ctx] score assemble {}", ctx.digest());
        boolean isAlgo = "algo".equals(kp.category);
        List<Dims.Dim> dims = isAlgo ? Dims.ALGO : Dims.NORMAL;
        String fullAnswer = isAlgo
                ? "【代码】\n" + (code == null || code.isEmpty() ? "（未写代码）" : code) + "\n\n【思路说明】\n" + (Utils.str(think, ""))
                : Utils.str(answerText, "");

        // ---------- AI 评分（LC 纯代码走代码评审 prompt） ----------
        String lcNoReal = take.lcNo != null ? take.lcNo : lcNoOf(kp);
        List<com.closeloop.ai.AiMessage> messages;
        if (kp.isLc) {
            LcExplain sol = lc.getSolutions().get(Utils.str(lcNoReal, ""));
            messages = LcPrompts.codeScore(lc.byNo(Utils.str(lcNoReal, "")), sol, Utils.str(code, ""));
        } else {
            messages = KbPrompts.score(q.question, q.answerPoints, docText, fullAnswer, dims);
        }
        JsonNode outcome = ai.chatJson(new AiCall(messages, 0.3, 4000, kp.isLc ? "lc-code-score" : "kb-score"), d -> {
            if (!d.path("total_score").isNumber()) throw new com.closeloop.ai.FormatReject("评分结果缺少 total_score");
            if (!d.path("standard_points").isArray() || d.path("standard_points").isEmpty()) {
                throw new com.closeloop.ai.FormatReject("评分结果缺少 standard_points");
            }
            if (!d.path("point_compare").isArray() || d.path("point_compare").isEmpty()) {
                throw new com.closeloop.ai.FormatReject("评分结果缺少 point_compare");
            }
        });

        Map<String, Object> norm = normalizeScore(outcome, dims, kp);

        // ---------- 闭环回写（同一临界区：评分落库 + 缺口检测消灭 + 队列移除） ----------
        return state.mutate(() -> {
            AppState st = state.state();
            AnswerApplier.Result updated = AnswerApplier.apply(st, kpId, questionId, q.question,
                    q.answerPoints, Utils.str(q.point, ""),
                    isAlgo ? "代码：" + Utils.str(code, "") + "\n思路：" + Utils.str(think, "") : Utils.str(answerText, ""),
                    norm, stallMark, spoken);

            // 缺口检测题：≥75 分 → 消灭该缺口（按实测判定，杜绝手动拍板）
            if (take.gapId != null && (int) norm.get("total_score") >= 75) {
                st.gaps.stream().filter(x -> take.gapId.equals(x.id) && x.resolvedAt == null).findFirst()
                        .ifPresent(g -> { g.resolvedAt = Dates.todayStr(); });
            }
            // 已答完题目从今日队列移除（同知识点其余题仍可答）
            AnswerApplier.popAnsweredQuestion(st, kpId, questionId);

            // 热路径：只落变更行，不走全量 JSON（问题：全量写 ≈70ms）
            Kp kpNow = st.kps.get(kpId);
            AnswerRecord rec = st.records.isEmpty() ? null : st.records.get(st.records.size() - 1);
            List<Gap> gapsForKp = new ArrayList<>();
            String today = Dates.todayStr();
            for (Gap g : st.gaps) {
                if (!kpId.equals(g.kpId)) continue;
                boolean touchedToday = (g.createdAt != null && g.createdAt.startsWith(today))
                        || (g.resolvedAt != null && g.resolvedAt.startsWith(today));
                if (touchedToday) gapsForKp.add(g);
            }
            state.saveHot(kpNow, rec, gapsForKp);

            return Utils.m(
                    "result", norm,
                    "recordId", updated.recordId(),
                    "kpState", updated.kpState(),
                    "session", updated.session(),
                    "kpi", Kpi.calc(st));
        });
    }

    // ---------- 取题 ----------

    private static class Take {
        QueueItem.Question question;
        String gapId;
        String lcNo;
    }

    private Take takeQuestion(String kpId, String questionId) {
        Take take = new Take();
        AppState st = state.state();

        // 1. 今日队列
        for (QueueItem it : st.queue) {
            if (kpId.equals(it.kpId)) {
                QueueItem.Question found = it.questions.stream().filter(x -> questionId.equals(x.id)).findFirst().orElse(null);
                if (found != null) { take.question = found; return take; }
            }
        }
        // 2. 地图直接出题会话（每道题只允许评分一次，取走即删，空会话整组删除）
        AppState.GenSession gs = st.genSessions.get(kpId);
        if (gs != null) {
            QueueItem.Question found = gs.questions.stream().filter(x -> questionId.equals(x.id)).findFirst().orElse(null);
            if (found != null) {
                gs.questions.removeIf(x -> questionId.equals(x.id));
                if (gs.questions.isEmpty()) st.genSessions.remove(kpId);
                take.question = found;
                return take;
            }
        }
        // 3. 力扣纯代码模式：LC-{no} 直接构造原题（不走 AI 出题）
        var matcher = LC_CODE_ID.matcher(questionId);
        if (matcher.matches()) {
            String no = matcher.group(1);
            LcDataService.LcProblem p = lc.byNo(no);
            if (p != null) {
                LcExplain sol = lc.getSolutions().get(no);
                QueueItem.Question synth = new QueueItem.Question();
                synth.id = questionId;
                synth.kpId = kpId;
                synth.type = "CODE";
                synth.difficulty = Utils.clamp(p.d + 2, 1, 5);
                synth.question = "力扣 " + p.no + ". " + p.title + "\n" + Utils.str(p.q, "");
                synth.answerPoints = sol != null && sol.answerPoints != null ? sol.answerPoints : new ArrayList<>();
                synth.anchor = "lc-" + p.no;
                synth.point = Utils.str(p.algo, "手写代码");
                take.question = synth;
                take.lcNo = no;
                return take;
            }
        }
        // 4. 缺口检测题（一次性，取走即删）
        QueueItem.Question gt = st.gapTests.get(questionId);
        if (gt != null) {
            st.gapTests.remove(questionId);
            take.question = gt;
            take.gapId = gt.gapId;
            return take;
        }
        return take;
    }

    private static String lcNoOf(Kp kp) {
        if (!kp.isLc || kp.lc == null) return null;
        Object no = kp.lc.get("no");
        return no == null ? null : String.valueOf(no);
    }

    // ---------- 评分结果规范化（面试官诊断报告 → snake 契约） ----------

    static Map<String, Object> normalizeScore(JsonNode o, List<Dims.Dim> dims, Kp kp) {
        double raw = o.path("total_score").asDouble(0);
        int total = (int) Math.round(clampD(raw, 0, 100));
        String levelIn = o.path("level").asText("");
        String level = switch (levelIn) {
            case "优秀", "良好", "及格", "不及格", "空白" -> levelIn;
            default -> scoreToLevel(raw);
        };
        String verdictIn = o.path("verdict").asText("");
        String verdict = switch (verdictIn) {
            case "pass", "followup", "fail" -> verdictIn;
            default -> raw >= 80 ? "pass" : raw >= 60 ? "followup" : "fail";
        };
        JsonNode profile = o.path("profile");
        Map<String, Object> out = Utils.m(
                "total_score", total,
                "level", level,
                "verdict", verdict,
                "coverage_score", (int) Math.round(o.path("coverage_score").asDouble(0)),
                "score_breakdown", o.path("score_breakdown").asText(""),
                "profile", Utils.m(
                        "tag", Utils.str(profile.path("tag").asText(""), "未掌握"),
                        "desc", profile.path("desc").asText("")),
                "standard_points", normPoints(o.path("standard_points")),
                "point_compare", normPoints(o.path("point_compare")),
                "covered_count", (int) Math.round(o.path("covered_count").asDouble(0)),
                "total_count", (int) Math.round(o.path("total_count").asDouble(0)),
                "feedback", normFeedback(o.path("feedback")),
                "rewrite_diff", normRewriteDiff(o.path("rewrite_diff")),
                "dimensions", normDimensions(o.path("dimensions"), dims),
                "missing_points", QuestionService.strList(o.path("missing_points"), 6),
                "errors", QuestionService.strList(o.path("errors"), 6),
                "gaps", normGaps(o.path("gaps")),
                "optimized_answer", Utils.cut(o.path("optimized_answer").asText(""), 1200),
                "advice", normalizeAdvice(o.path("advice"), kp));
        // LC 题无知识库文档：advice 改指向 LC 精讲页，前端据此跳转 #/lc/:no
        if (kp.isLc) {
            String no = lcNoOf(kp);
            String title = kp.lc == null ? "" : Utils.str(kp.lc.get("title"), "");
            out.put("advice", Utils.m(
                    "level", "review",
                    "action", "回到 LC 精讲复习",
                    "target", "",
                    "read", "重看力扣 " + (no != null ? no + "." + title : "") + " 零基础精讲与逐步思考链",
                    "practice", "重做本题手写代码，直到能独立写出最优解并讲清思路"));
            out.put("lcNo", no == null ? null : Integer.parseInt(no));
        }
        return out;
    }

    private static List<Map<String, Object>> normPoints(JsonNode arr) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode x : arr) {
                String status = x.path("status").asText("");
                out.add(Utils.m(
                        "id", x.path("id").asText(""),
                        "text", x.path("text").asText(""),
                        "why", x.path("why").asText(""),
                        "status", switch (status) {
                            case "covered", "partial", "missing", "wrong" -> status;
                            default -> "missing";
                        },
                        "mine", x.path("mine").asText(""),
                        "diff", x.path("diff").asText(""),
                        "fix", x.path("fix").asText("")));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> normFeedback(JsonNode arr) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr.isArray()) {
            int n = Math.min(3, arr.size());
            for (int i = 0; i < n; i++) {
                JsonNode f = arr.get(i);
                String improve = f.path("improve").asText("").replaceAll("^下次(这样|这么)答[:：]?\\s*", "");
                out.add(Utils.m(
                        "issue", Utils.cut(f.path("issue").asText(""), 300),
                        "improve", Utils.cut(improve, 400)));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> normRewriteDiff(JsonNode arr) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr.isArray()) {
            int n = Math.min(10, arr.size());
            for (int i = 0; i < n; i++) {
                JsonNode x = arr.get(i);
                String type = x.path("type").asText("");
                out.add(Utils.m(
                        "type", switch (type) {
                            case "keep", "rewrite", "add", "fix", "reorder" -> type;
                            default -> "rewrite";
                        },
                        "where", Utils.cut(x.path("where").asText(""), 80),
                        "original", Utils.cut(x.path("original").asText(""), 300),
                        "optimized", Utils.cut(x.path("optimized").asText(""), 300),
                        "note", Utils.cut(x.path("note").asText(""), 200)));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> normDimensions(JsonNode arr, List<Dims.Dim> dims) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr.isArray()) {
            int n = Math.min(dims.size(), arr.size());
            for (int i = 0; i < n; i++) {
                JsonNode d = arr.get(i);
                out.add(Utils.m(
                        "name", d.path("name").asText(""),
                        "score", (int) Math.round(d.path("score").asDouble(0)),
                        "max", d.path("max").asDouble(0),
                        "comment", d.path("comment").asText("")));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> normGaps(JsonNode arr) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr.isArray()) {
            int n = Math.min(5, arr.size());
            for (int i = 0; i < n; i++) {
                JsonNode g = arr.get(i);
                String label = g.path("label").asText("");
                out.add(Utils.m(
                        "label", Dims.GAP_LABELS.contains(label) ? label : "表达卡顿",
                        "detail", g.path("detail").asText("")));
            }
        }
        return out;
    }

    static Map<String, Object> normalizeAdvice(JsonNode advice, Kp kp) {
        if (advice == null || !advice.isObject()) return null;
        String level = advice.path("level").asText("");
        String target = advice.path("target").asText("");
        if (!target.contains("#") && kp != null && kp.path != null && !kp.path.isEmpty()) {
            target = target + "#";
        }
        return Utils.m(
                "level", switch (level) {
                    case "review", "practice", "extend" -> level;
                    default -> "review";
                },
                "action", Utils.str(advice.path("action").asText(""), "跳转精读"),
                "target", target,
                "read", Utils.cut(advice.path("read").asText(""), 120),
                "practice", Utils.cut(advice.path("practice").asText(""), 160));
    }

    static String scoreToLevel(double s) {
        if (s >= 90) return "优秀";
        if (s >= 75) return "良好";
        if (s >= 60) return "及格";
        if (s >= 40) return "不及格";
        return "空白";
    }

    private static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

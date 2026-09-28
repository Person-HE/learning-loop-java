package com.closeloop.service;

import com.closeloop.ai.AiCall;
import com.closeloop.ai.AiClient;
import com.closeloop.ai.FormatReject;
import com.closeloop.ai.prompt.LcPrompts;
import com.closeloop.common.ApiException;
import com.closeloop.common.Utils;
import com.closeloop.knowledge.LcDataService;
import com.closeloop.knowledge.LcDataService.LcProblem;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.LcExplain;
import com.closeloop.state.model.QueueItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 力扣 100 题模块：题列表（复用知识点复习状态）/ 详情 / AI 精讲（懒生成缓存）/ 手搓代码入口 / 侧边 AI 答疑。
 * LC 闭环（SM-2、缺口、同题复答）由 lc-{no} 知识点承担，与「数据结构与算法」八股模块彻底分离。
 */
@Service
public class LcService {

    private final AiClient ai;
    private final LcDataService lc;
    private final StateManager state;

    public LcService(AiClient ai, LcDataService lc, StateManager state) {
        this.ai = ai;
        this.lc = lc;
        this.state = state;
    }

    /** 题列表：题库元数据 + 每题复习/缺口状态 */
    public Map<String, Object> list() {
        AppState st = state.state();
        List<Map<String, Object>> out = new ArrayList<>();
        for (LcProblem p : lc.getProblems()) {
            Kp kp = st.kps.get("lc-" + p.no);
            Kp.KpState s = kp != null ? kp.state : null;
            boolean explained = st.lcExplains.containsKey(String.valueOf(p.no)) || lc.getSolutions().containsKey(String.valueOf(p.no));
            out.add(Utils.m(
                    "no", p.no, "title", p.title, "slug", p.slug, "d", p.d,
                    "category", p.category, "algo", p.algo, "url", p.leetcodeUrl(),
                    "status", s != null ? s.status : "new",
                    "due", s != null ? s.due : null,
                    "lastScore", s != null ? s.lastScore : null,
                    "reviews", s != null ? s.reviews : 0,
                    "explained", explained));
        }
        return Utils.m("total", out.size(), "problems", out);
    }

    /** 详情：题面 + 精讲（内置或 AI 缓存）+ 学习状态 + 未解决缺口 */
    public Map<String, Object> detail(String no) {
        AppState st = state.state();
        LcProblem p = lc.byNo(no);
        if (p == null) throw ApiException.notFound("题目不存在");
        Kp kp = st.kps.get("lc-" + no);
        LcExplain cached = st.lcExplains.get(no) != null ? st.lcExplains.get(no) : lc.getSolutions().get(no);
        Gap openGap = kp != null
                ? st.gaps.stream().filter(g -> kp.id.equals(g.kpId) && g.resolvedAt == null).findFirst().orElse(null)
                : null;
        Map<String, Object> problem = Utils.m(
                "no", p.no, "title", p.title, "slug", p.slug, "d", p.d,
                "category", p.category, "algo", p.algo,
                "q", p.q, "ex", p.ex, "c", p.c, "url", p.leetcodeUrl());
        return Utils.m(
                "problem", problem,
                "kpId", kp != null ? kp.id : null,
                "state", kp != null ? kp.state : null,
                "openGap", openGap,
                "explain", cached);
    }

    /** AI 精讲（真实调用 + 缓存）：零基础思考链 + 多解法 + 动画帧 */
    public LcExplain explain(String no) {
        AppState st0 = state.state();
        LcProblem p = lc.byNo(no);
        if (p == null) throw ApiException.notFound("题目不存在");
        LcExplain cached = st0.lcExplains.get(no);
        if (cached != null) return cached;
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key");

        JsonNode data = ai.chatJson(new AiCall(LcPrompts.explain(p), 0.5, 6000, "lc-explain"), d -> {
            if (!d.path("thinking").isArray() || d.path("thinking").size() < 5) {
                throw new FormatReject("精讲缺少思考链（thinking）");
            }
            if (!d.path("solutions").isArray() || d.path("solutions").isEmpty()) {
                throw new FormatReject("精讲缺少解法（solutions）");
            }
        });

        LcExplain norm = new LcExplain();
        norm.point = Utils.cut(data.path("point").asText(""), 40);
        JsonNode thinking = data.path("thinking");
        if (thinking.isArray()) {
            int n = Math.min(14, thinking.size());
            for (int i = 0; i < n; i++) {
                JsonNode s = thinking.get(i);
                norm.thinking.add(Utils.m(
                        "t", Utils.cut(s.path("t").asText(""), 80),
                        "w", Utils.cut(s.path("w").asText(""), 600)));
            }
        }
        JsonNode solutions = data.path("solutions");
        if (solutions.isArray()) {
            int n = Math.min(3, solutions.size());
            for (int i = 0; i < n; i++) {
                JsonNode s = solutions.get(i);
                norm.solutions.add(Utils.m(
                        "name", Utils.cut(s.path("name").asText(""), 40),
                        "idea", Utils.cut(s.path("idea").asText(""), 300),
                        "code", Utils.cut(s.path("code").asText(""), 3000),
                        "time", Utils.cut(s.path("time").asText(""), 20),
                        "space", Utils.cut(s.path("space").asText(""), 20),
                        "note", Utils.cut(s.path("note").asText(""), 200)));
            }
        }
        JsonNode anim = data.path("animation");
        if (anim.isObject() && anim.path("frames").isArray()) {
            JsonNode frames = anim.path("frames");
            int n = Math.min(12, frames.size());
            List<Map<String, Object>> fs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                JsonNode f = frames.get(i);
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("desc", Utils.cut(f.path("desc").asText(""), 200));
                var it = f.fields();
                while (it.hasNext()) {
                    var e = it.next();
                    if ("desc".equals(e.getKey())) continue;
                    frame.put(e.getKey(), e.getValue().isObject() || e.getValue().isArray()
                            ? e.getValue().toString() : jsValue(e.getValue()));
                }
                fs.add(frame);
            }
            norm.animation = Utils.m("type", Utils.cut(anim.path("type").asText("数组"), 10), "frames", fs);
        }
        norm.answerPoints = QuestionService.strList(data.path("answerPoints"), 6);
        norm.edge = QuestionService.strList(data.path("edge"), 5);
        norm.tips = QuestionService.strList(data.path("tips"), 4);

        state.mutateVoid(() -> state.state().lcExplains.put(no, norm));
        return norm;
    }

    private static Object jsValue(JsonNode v) {
        if (v.isNumber()) return v.isIntegralNumber() ? (Object) v.asInt() : (Object) v.asDouble();
        if (v.isBoolean()) return v.asBoolean();
        if (v.isNull() || v.isMissingNode()) return null;
        return v.asText();
    }

    /** 纯手搓代码模式：返回本题原题作为 1 道 CODE 题（不调 AI 出题；提交走 /answer/score 的 LC-{no} 分支） */
    public Map<String, Object> practice(String no) {
        AppState st = state.state();
        LcProblem p = lc.byNo(no);
        if (p == null) throw ApiException.notFound("题目不存在");
        String kpId = "lc-" + no;
        if (!st.kps.containsKey(kpId)) throw ApiException.notFound("知识点未注册，请先刷新");
        LcExplain sol = lc.getSolutions().get(no);
        QueueItem.Question q = new QueueItem.Question();
        q.id = "LC-" + no;
        q.kpId = kpId;
        q.type = "CODE";
        q.difficulty = Utils.clamp(p.d + 2, 1, 5);
        q.question = "力扣 " + p.no + ". " + p.title + "\n" + Utils.str(p.q, "");
        q.answerPoints = sol != null && sol.answerPoints != null ? sol.answerPoints : new ArrayList<>();
        q.anchor = "lc-" + p.no;
        q.point = Utils.str(p.algo, "手写代码");
        return Utils.m("item", Utils.m(
                "kpId", kpId,
                "kind", "lc",
                "reason", p.no + "." + p.title + " 手写代码",
                "qIdx", 0,
                "questions", List.of(q),
                "genError", null));
    }

    /** 侧边 AI 答疑：带题面 + 精讲上下文，自然语言回答（非 JSON） */
    public Map<String, Object> ask(String no, List<Map<String, Object>> messages) {
        LcProblem p = lc.byNo(no);
        if (p == null) throw ApiException.notFound("题目不存在");
        Map<String, Object> last = messages == null || messages.isEmpty() ? null : messages.get(messages.size() - 1);
        if (last == null || Utils.str(last.get("content"), "").trim().isEmpty()) {
            throw ApiException.badRequest("请输入你的疑问");
        }
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key");
        AppState st = state.state();
        LcExplain sol = lc.getSolutions().get(no) != null ? lc.getSolutions().get(no) : st.lcExplains.get(no);
        String out = ai.chat(new AiCall(LcPrompts.ask(p, sol, messages), 0.4, 1200, "lc-ask"));
        return Utils.m("reply", out == null ? "" : out.trim());
    }
}

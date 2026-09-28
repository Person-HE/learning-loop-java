package com.closeloop.service;

import com.closeloop.ai.AiCall;
import com.closeloop.ai.AiClient;
import com.closeloop.ai.FormatReject;
import com.closeloop.ai.prompt.Dims;
import com.closeloop.ai.prompt.ProjectPrompts;
import com.closeloop.common.ApiException;
import com.closeloop.common.Dates;
import com.closeloop.common.Utils;
import com.closeloop.config.AppProperties;
import com.closeloop.project.ResumeData;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.ProjectState;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 项目面试服务：简历/切面总览、项目深挖出题（缓存供评分）、面试官模型评分（SM-2 简化调度回写切面状态）、
 * 简历评分（Skills 评分模型）。与模拟面试（MockInterviewService）共享评分规范化逻辑。
 */
@Service
public class ProjectInterviewService {

    private final AiClient ai;
    private final StateManager state;
    private final AppProperties props;

    public ProjectInterviewService(AiClient ai, StateManager state, AppProperties props) {
        this.ai = ai;
        this.state = state;
        this.props = props;
    }

    /** /projects 总览：结构化简历 + 原始 HTML + 切面状态 */
    public Map<String, Object> overview() {
        ProjectState p = state.state().project;
        String resumeHtml;
        try {
            resumeHtml = java.nio.file.Files.readString(java.nio.file.Path.of(props.resumeHtml()));
        } catch (Exception e) {
            resumeHtml = null;
        }
        return Utils.m(
                "resume", ResumeData.resumeMap(props.resumeHtml()),
                "resumeVersion", ResumeData.RESUME_VERSION,
                "resumeHtml", resumeHtml,
                "facets", ResumeData.facets().stream().map(f -> Utils.m("id", f.id(), "name", f.name(), "desc", f.desc())).toList(),
                "states", p.facets,
                "sessionsCount", p.sessions.size(),
                "resumeScore", p.resumeScore,
                "today", Dates.todayStr());
    }

    // ---------- 深挖出题 ----------

    public Map<String, Object> generateQuestions(String facetId) {
        ResumeData.Facet facet = facetOrDefault(facetId);
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");
        String resumeText = Utils.cut(ResumeData.buildResumeText(), 6000);
        JsonNode data = ai.chatJson(new AiCall(ProjectPrompts.gen(facet, resumeText), 0.7, 3000, "project-questions"), d -> {
            if (!d.isArray()) throw new FormatReject("出题结果不是数组");
        });
        long ts = System.currentTimeMillis();
        List<Map<String, Object>> qs = new ArrayList<>();
        int n = Math.min(5, data.size());
        for (int i = 0; i < n; i++) {
            JsonNode q = data.get(i);
            String question = q.path("question").asText("").trim();
            if (question.length() < 6) continue;
            qs.add(Utils.m(
                    "id", "PQ-" + ts + "-" + (i + 1),
                    "type", q.path("type").asText("难点"),
                    "targetTech", Utils.cut(q.path("targetTech").asText(""), 40),
                    "question", question,
                    "answerPoints", QuestionService.strList(q.path("answerPoints"), 6),
                    "followUps", QuestionService.strList(q.path("followUps"), 4)));
        }
        if (qs.isEmpty()) throw ApiException.internal("AI 未生成有效题目，请重试");
        final String facetIdFinal = facet.id();
        state.mutateVoid(() -> {
            ProjectState.Cache cache = new ProjectState.Cache();
            cache.facetId = facetIdFinal;
            cache.ts = ts;
            cache.questions = qs;
            state.state().project.cache = cache;
        });
        return Utils.m("facetId", facetIdFinal, "questions", qs);
    }

    // ---------- 深挖评分 ----------

    public Map<String, Object> score(String facetId, Integer qIdx, String answer) {
        ProjectState.Cache cache = state.state().project.cache;
        if (cache == null || cache.questions == null || qIdx == null || qIdx < 0 || qIdx >= cache.questions.size()) {
            throw ApiException.notFound("题目缓存已失效，请重新出题");
        }
        Map<String, Object> q = cache.questions.get(qIdx);
        ResumeData.Facet facet = facetOrDefault(facetId);
        if (answer == null || answer.trim().length() < 10) throw ApiException.badRequest("回答太短");
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");

        String question = Utils.str(q.get("question"), "");
        @SuppressWarnings("unchecked")
        List<String> answerPoints = (List<String>) q.getOrDefault("answerPoints", List.of());
        JsonNode outcome = ai.chatJson(new AiCall(
                ProjectPrompts.score(facet.name(), question, answerPoints, Utils.cut(answer, 3000), ResumeData.buildResumeText()),
                0.3, 3000, "project-score"), d -> {
            if (!d.path("total100").isNumber() && !d.path("total10").isNumber()) throw new FormatReject("评分结果缺少总分");
        });

        Map<String, Object> norm = normalizeInterviewScore(outcome, true);
        ProjectState.FacetState facetState = state.mutate(() -> {
            ProjectState p = state.state().project;
            recordFacetResult(p, facet, norm, Utils.m(
                    "id", "PS-" + System.currentTimeMillis(),
                    "date", Dates.todayStr(),
                    "facetId", facet.id(),
                    "facetName", facet.name(),
                    "question", Utils.cut(question, 300),
                    "answer", Utils.cut(answer, 3000),
                    "total100", norm.get("total100"),
                    "level", norm.get("level"),
                    "gaps", norm.get("gaps")));
            return p.facets.get(facet.id());
        });
        return Utils.m("result", norm, "facetState", facetState);
    }

    // ---------- 简历评分 ----------

    public Map<String, Object> resumeScore() {
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");
        JsonNode outcome = ai.chatJson(new AiCall(
                ProjectPrompts.resumeScore(Utils.cut(ResumeData.buildResumeText(), 6000), "Java 后端开发（应届/实习，加分：AI Agent）"),
                0.3, 3000, "resume-score"), d -> {
            if (!d.path("total").isNumber()) throw new FormatReject("评分结果缺少 total");
        });
        Map<String, Object> norm = Utils.m(
                "total", (int) Math.round(clampD(outcome.path("total").asDouble(0), 0, 100)),
                "dimensions", normScoreDimensions(outcome.path("dimensions"), false),
                "issues", normObjList(outcome.path("issues"), 5, new String[]{"type", "detail"}),
                "suggestions", normObjList(outcome.path("suggestions"), 6, new String[]{"title", "detail"}),
                "highlight", QuestionService.strList(outcome.path("highlight"), 2),
                "at", java.time.Instant.now().toString());
        state.mutateVoid(() -> state.state().project.resumeScore = norm);
        return Utils.m("result", norm);
    }

    // ---------- 共享规范化（深挖评分与模拟面试总结同源） ----------

    /** 面试官评分规范化：total10/total100 双分 + 六维 + STAR + review + 缺口 + 建议 + 通过率 */
    public static Map<String, Object> normalizeInterviewScore(JsonNode o, boolean withOptimized) {
        double t10 = Math.max(0, Math.min(10, firstPositive(o.path("total10").asDouble(0), o.path("total100").asDouble(0) / 10)));
        int t100 = (int) Math.round(clampD(firstPositive(o.path("total100").asDouble(0), t10 * 10), 0, 100));
        String levelIn = o.path("level").asText("");
        String level = switch (levelIn) {
            case "优秀", "良好", "及格", "不及格", "空白" -> levelIn;
            default -> GradingService.scoreToLevel(t100);
        };
        Map<String, Object> norm = Utils.m(
                "total10", Math.round(t10 * 10) / 10.0,
                "total100", t100,
                "level", level,
                "dimensions", normScoreDimensions(o.path("dimensions"), true),
                "starScore", (int) Math.round(o.path("starScore").asDouble(0)),
                "review", normReview(o.path("review")),
                "strengths", QuestionService.strList(o.path("strengths"), 3),
                "issues", QuestionService.strList(o.path("issues"), 4),
                "gaps", normGaps(o.path("gaps")),
                "advice", normObjList(o.path("advice"), 4, new String[]{"title", "detail"}),
                "passRate", (int) Math.round(o.path("passRate").asDouble(0)));
        if (withOptimized) {
            // optimizedAnswer 插在 advice 之前（与 Node 版键序一致：gaps → optimizedAnswer → advice → passRate）
            Map<String, Object> ordered = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> e : norm.entrySet()) {
                if ("advice".equals(e.getKey()) || "passRate".equals(e.getKey())) continue;
                ordered.put(e.getKey(), e.getValue());
            }
            ordered.put("optimizedAnswer", Utils.cut(o.path("optimizedAnswer").asText(""), 1000));
            ordered.put("advice", norm.get("advice"));
            ordered.put("passRate", norm.get("passRate"));
            norm = ordered;
        }
        return norm;
    }

    static List<Map<String, Object>> normScoreDimensions(JsonNode arr, boolean withWeight) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr.isArray()) {
            int n = Math.min(withWeight ? 6 : Dims.RESUME.size(), arr.size());
            for (int i = 0; i < n; i++) {
                JsonNode d = arr.get(i);
                if (withWeight) {
                    out.add(Utils.m(
                            "name", d.path("name").asText(""),
                            "score", (int) Math.round(d.path("score").asDouble(0)),
                            "weight", d.path("weight").asDouble(0),
                            "comment", d.path("comment").asText("")));
                } else {
                    out.add(Utils.m(
                            "name", d.path("name").asText(""),
                            "score", (int) Math.round(d.path("score").asDouble(0)),
                            "max", d.path("max").asDouble(0),
                            "comment", d.path("comment").asText("")));
                }
            }
        }
        return out;
    }

    static List<Map<String, Object>> normReview(JsonNode arr) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr.isArray()) {
            int n = Math.min(8, arr.size());
            for (int i = 0; i < n; i++) {
                JsonNode r = arr.get(i);
                String q = Utils.cut(r.path("q").asText(""), 300);
                String judge = Utils.cut(r.path("judge").asText(""), 300);
                if (q.isEmpty() && judge.isEmpty()) continue;
                out.add(Utils.m(
                        "q", q,
                        "candidate", Utils.cut(r.path("candidate").asText(""), 400),
                        "judge", judge));
            }
        }
        return out;
    }

    static List<Map<String, Object>> normGaps(JsonNode arr) {
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

    static List<Map<String, Object>> normObjList(JsonNode arr, int limit, String[] keys) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr.isArray()) {
            int n = Math.min(limit, arr.size());
            for (int i = 0; i < n; i++) {
                JsonNode x = arr.get(i);
                Object[] kv = new Object[keys.length * 2];
                for (int k = 0; k < keys.length; k++) kv[k * 2] = keys[k];
                for (int k = 0; k < keys.length; k++) kv[k * 2 + 1] = x.path(keys[k]).asText("");
                out.add(Utils.m(kv));
            }
        }
        return out;
    }

    /** 切面调度回写（≥90→7天 / ≥75→3天 / ≥60→1天 / 否则今天）+ 会话记录（≤500 条） */
    @SuppressWarnings("unchecked")
    public static ProjectState.FacetState recordFacetResult(ProjectState p, ResumeData.Facet facet,
                                                            Map<String, Object> norm, Map<String, Object> sessionBase) {
        p.sessions.add(sessionBase);
        if (p.sessions.size() > 500) p.sessions.subList(0, p.sessions.size() - 500).clear();

        int t100 = (int) norm.get("total100");
        int dueDays = t100 >= 90 ? 7 : t100 >= 75 ? 3 : t100 >= 60 ? 1 : 0;
        ProjectState.FacetState prev = p.facets.get(facet.id());
        ProjectState.FacetState fs = prev != null ? prev : new ProjectState.FacetState();
        fs.reviews = fs.reviews + 1;
        fs.lastScore = t100;
        fs.lastLevel = (String) norm.get("level");
        fs.due = Dates.todayStr(dueDays);
        List<Map<String, Object>> hist = new ArrayList<>(fs.history.subList(Math.max(0, fs.history.size() - 20), fs.history.size()));
        Map<String, Object> he = new java.util.LinkedHashMap<>();
        he.put("date", sessionBase.get("date"));
        he.put("score", t100);
        he.put("level", norm.get("level"));
        if (sessionBase.get("kind") != null) he.put("kind", "mock");
        hist.add(he);
        fs.history = hist;
        p.facets.put(facet.id(), fs);
        return fs;
    }

    public ResumeData.Facet facetOrDefault(String facetId) {
        List<ResumeData.Facet> facets = ResumeData.facets();
        if (facetId == null) return facets.get(0);
        return facets.stream().filter(f -> f.id().equals(facetId)).findFirst().orElse(facets.get(0));
    }

    static double firstPositive(double a, double b) {
        return a != 0 ? a : b;
    }

    static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

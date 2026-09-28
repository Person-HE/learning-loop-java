package com.closeloop.service;

import com.closeloop.common.ApiException;
import com.closeloop.common.Dates;
import com.closeloop.common.Utils;
import com.closeloop.config.AiProperties;
import com.closeloop.config.AppProperties;
import com.closeloop.knowledge.KbService;
import com.closeloop.knowledge.KnowledgeBase;
import com.closeloop.learning.Kpi;
import com.closeloop.learning.PlanBuilder;
import com.closeloop.learning.QueueBuilder;
import com.closeloop.learning.Priority;
import com.closeloop.project.ResumeData;
import com.closeloop.state.StateManager;
import com.closeloop.state.model.AnswerRecord;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Kp;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态视图与全局设置：/state 公共快照、/records、/settings、/state/reset、/kb 系列。
 * publicState 保持与 Node 版完全一致的键序与形状，前端零改动。
 */
@Service
public class StateViewService {

    private final StateManager state;
    private final KbService kb;
    private final AppProperties props;
    private final AiProperties aiProps;
    private final com.closeloop.ai.AiClient ai;

    public StateViewService(StateManager state, KbService kb, AppProperties props, AiProperties aiProps, com.closeloop.ai.AiClient ai) {
        this.state = state;
        this.kb = kb;
        this.props = props;
        this.aiProps = aiProps;
        this.ai = ai;
    }

    // ---------- /state 公共快照 ----------

    public Map<String, Object> publicState() {
        AppState st = state.state();
        KnowledgeBase kbn = kb.getKb(false);
        Map<String, Object> kpsMeta = new LinkedHashMap<>();
        for (Map.Entry<String, Kp> e : st.kps.entrySet()) {
            Kp k = e.getValue();
            kpsMeta.put(e.getKey(), Utils.m(
                    "id", k.id, "title", k.title, "domain", k.domain, "chapter", k.chapter,
                    "category", k.category, "difficulty", k.difficulty, "hot", k.hot,
                    "path", k.path, "kbStatus", k.kbStatus, "state", k.state));
        }
        String key = Utils.str(aiProps.apiKey(), "");
        Map<String, Object> settings = Utils.m(
                "apiKey", "****" + (key.length() > 4 ? key.substring(key.length() - 4) : key),
                "baseUrl", aiProps.baseUrl(),
                "model", aiProps.model(),
                "weights", Utils.m("java", st.settings.weights.java, "algo", st.settings.weights.algo, "ai", st.settings.weights.ai),
                "firstUseDate", st.settings.firstUseDate,
                "freezeDays", st.settings.freezeDays,
                "frozen", st.settings.frozen,
                "mode", st.settings.mode,
                "missedDays", st.settings.missedDays,
                "minimalStreak", st.settings.minimalStreak,
                "maxQueue", st.settings.maxQueue,
                "aiLocked", true);
        return Utils.m(
                "kpi", Kpi.calc(st),
                "sessionToday", st.sessionToday,
                "settings", settings,
                "queue", QueueBuilder.sanitize(st.queue),
                "gaps", st.gaps,
                "recordsCount", st.records.size(),
                "kpsMeta", kpsMeta,
                "project", Utils.m(
                        "facets", ResumeData.facets().stream().map(f -> Utils.m("id", f.id(), "name", f.name(), "desc", f.desc())).toList(),
                        "states", st.project.facets,
                        "sessionsCount", st.project.sessions.size(),
                        "resumeScore", st.project.resumeScore),
                "kbSummary", Utils.m(
                        "root", kbn.root,
                        "exists", kbn.exists,
                        "domains", kbn.domains,
                        "totalDocs", kbn.totalDocs,
                        "plannedPaths", kbn.plannedPaths,
                        "lowPriorityDomains", Priority.LOW_PRIORITY_DOMAINS),
                "today", Dates.todayStr(),
                "mode", st.settings.mode,
                "frozen", st.settings.frozen,
                "missLeftToMinimal", Math.max(0, 3 - st.settings.missedDays));
    }

    // ---------- 队列 ----------

    public Map<String, Object> buildQueue() {
        return state.mutate(() -> Utils.m(
                "queue", QueueBuilder.sanitize(QueueBuilder.build(state.state())),
                "mode", state.state().settings.mode));
    }

    /** 今日选题计划（只读预览，不改队列） */
    public Map<String, Object> buildPlan() {
        return PlanBuilder.plan(state.state());
    }

    /** 确认选题：锁定卡不砍、勾选按序补到 maxQ，写回队列并持久化 */
    public Map<String, Object> confirmPlan(List<String> picks) {
        return state.mutate(() -> PlanBuilder.confirm(state.state(), picks));
    }

    /** 预生成队列全部题目：并发 2 的 worker 池 + 全局生成锁（对应 Node Promise.all([worker,worker])） */
    public Map<String, Object> prepareQueue(QuestionService questionService) {
        AppState st0 = state.state();
        if (st0.queue.isEmpty()) QueueBuilder.build(st0);
        if (!ai.hasKey()) throw ApiException.badRequest("未配置 AI API Key，请到「设置」页填写");
        List<com.closeloop.state.model.QueueItem> items = new ArrayList<>(state.state().queue);

        List<Map<String, Object>> results = state.withGenLock(() -> {
            List<Map<String, Object>> out = java.util.Collections.synchronizedList(new ArrayList<>());
            java.util.concurrent.atomic.AtomicInteger idx = new java.util.concurrent.atomic.AtomicInteger();
            Runnable worker = () -> {
                while (true) {
                    int i = idx.getAndIncrement();
                    if (i >= items.size()) return;
                    com.closeloop.state.model.QueueItem it = items.get(i);
                    com.closeloop.state.model.QueueItem existing = state.state().queue.stream()
                            .filter(x -> it.kpId.equals(x.kpId)).findFirst().orElse(null);
                    if (existing != null && existing.questions != null && !existing.questions.isEmpty()) {
                        out.add(Utils.m("kpId", it.kpId, "ok", true, "count", existing.questions.size(), "skipped", true));
                        continue;
                    }
                    try {
                        var qs = questionService.prepareItemQuestions(it.kpId);
                        out.add(Utils.m("kpId", it.kpId, "ok", true, "count", qs.size()));
                    } catch (RuntimeException e) {
                        String msg = Utils.str(e.getMessage(), "生成失败");
                        state.mutateVoid(() -> {
                            com.closeloop.state.model.QueueItem qit = state.state().queue.stream()
                                    .filter(x -> it.kpId.equals(x.kpId)).findFirst().orElse(null);
                            if (qit != null) qit.genError = msg;
                        });
                        out.add(Utils.m("kpId", it.kpId, "ok", false, "error", msg));
                    }
                }
            };
            Thread w1 = new Thread(worker);
            Thread w2 = new Thread(worker);
            w1.start();
            w2.start();
            try {
                w1.join();
                w2.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw ApiException.internal("生成被中断，请重试");
            }
            return out;
        });
        return Utils.m("results", results, "queue", QueueBuilder.sanitize(state.state().queue));
    }

    // ---------- /records ----------

    public Map<String, Object> records() {
        AppState st = state.state();
        List<Object> sessions = new ArrayList<>(st.sessions);
        if (st.sessionToday.count > 0) {
            sessions.add(Utils.m(
                    "date", st.sessionToday.date,
                    "count", st.sessionToday.count,
                    "avg", st.sessionToday.avg,
                    "newG", st.sessionToday.newG,
                    "resG", st.sessionToday.resG,
                    "min", st.sessionToday.min,
                    "spoken", st.sessionToday.totalSpoken == st.sessionToday.count,
                    "mode", st.settings.mode));
        }
        List<AnswerRecord> recent = new ArrayList<>(st.records.subList(Math.max(0, st.records.size() - 200), st.records.size()));
        java.util.Collections.reverse(recent);
        return Utils.m(
                "sessions", sessions,
                "sessionToday", st.sessionToday,
                "records", recent,
                "mastery", Kpi.categoryMastery(st),
                "kpi", Kpi.calc(st));
    }

    public Map<String, Object> markStall(String recordId, Object mark) {
        return state.mutate(() -> {
            AppState st = state.state();
            AnswerRecord r = st.records.stream().filter(x -> recordId.equals(x.id)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("记录不存在"));
            r.stallMark = mark == null ? null : mark;
            // mysql 模式 save() 只写 blob，记录变更需显式热写，否则卡壳标记重启即丢
            state.saveHot(st.kps.get(r.kpId), r, null);
            return Utils.m("ok", true, "record", Utils.m("id", r.id, "stallMark", r.stallMark));
        });
    }

    // ---------- /settings /reset ----------

    /** AI 配置已固定（app.ai），忽略任何覆盖请求；冻结期内禁改权重与模式 */
    public Map<String, Object> saveSettings(Map<String, Object> weights, String mode) {
        return state.mutate(() -> {
            AppState st = state.state();
            boolean frozen = st.settings.frozen;
            if (!frozen && weights != null && !weights.isEmpty()) {
                int j = numOr(weights.get("java"), -1);
                int a = numOr(weights.get("algo"), -1);
                int i = numOr(weights.get("ai"), -1);
                if (j + a + i == 100 && j >= 0 && a >= 0 && i >= 0) {
                    st.settings.weights = new AppState.Weights(j, a, i);
                }
            }
            if (!frozen && ("standard".equals(mode) || "minimal".equals(mode))) {
                st.settings.mode = mode;
            }
            return Utils.m("ok", true, "frozen", frozen, "aiLocked", true, "settings", publicState().get("settings"));
        });
    }

    private static int numOr(Object v, int def) {
        return v instanceof Number n ? (int) n.doubleValue() : def;
    }

    public Map<String, Object> reset() {
        state.reset(kb.getKb(false));
        return Utils.m("ok", true, "mode", state.state().settings.mode);
    }

    // ---------- /kb ----------

    public Map<String, Object> kbView(boolean refresh) {
        KnowledgeBase kbn = kb.getKb(refresh);
        return Utils.m(
                "root", kbn.root,
                "exists", kbn.exists,
                "domains", kbn.domains,
                "totalDocs", kbn.totalDocs,
                "plannedPaths", kbn.plannedPaths,
                "kps", kb.orderKpsByPath(kbn.kps, "java"));
    }

    public Map<String, Object> kbRefresh() {
        KnowledgeBase kbn = kb.getKb(true);
        return state.mutate(() -> {
            int added = state.mergeKbKps(state.state(), kbn);
            return Utils.m("added", added, "totalDocs", kbn.totalDocs, "domains", kbn.domains.size());
        });
    }

    /** 知识库文档原文预览（学习系统内直接查看） */
    public Map<String, Object> kbDoc(String kpId) {
        Kp kp = state.state().kps.get(kpId);
        if (kp == null) throw ApiException.notFound("知识点不存在");
        String doc = kb.readDoc(kp);
        if (doc == null) throw ApiException.notFound("知识库文档缺失：" + kp.path);
        List<Map<String, Object>> assets = new ArrayList<>();
        for (Map<String, Object> a : kp.assets) {
            String rel = Utils.str(a.get("rel"), "");
            String url = "/kb-assets/" + java.util.Arrays.stream(rel.split("/"))
                    .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                    .reduce((x, y) -> x + "/" + y).orElse("");
            assets.add(Utils.m("name", a.get("name"), "kind", a.get("kind"), "url", url));
        }
        return Utils.m(
                "kpId", kp.id, "title", kp.title, "domain", kp.domain, "chapter", kp.chapter,
                "path", kp.path, "anchors", kp.anchors, "assets", assets,
                "text", Utils.cut(doc, 120000), "truncated", doc.length() > 120000);
    }

    // ---------- 静态资源：知识库图片（/kb-assets/**，与 Node index.js 相同的白名单与防穿越） ----------

    private static final java.util.Set<String> KB_ASSET_EXT = java.util.Set.of(".svg", ".html", ".png", ".jpg", ".jpeg", ".gif", ".webp");

    public Path resolveKbAsset(String relPath) {
        String rel = relPath.replace('\\', '/');
        Path root = props.kbRootPath().toAbsolutePath().normalize();
        Path target = root.resolve(rel).normalize();
        String ext = rel.contains(".") ? rel.substring(rel.lastIndexOf('.')).toLowerCase() : "";
        if (!KB_ASSET_EXT.contains(ext)) throw ApiException.notFound("资源类型不允许");
        if (!target.startsWith(root)) throw ApiException.badRequest("非法资源路径");
        if (!Files.isRegularFile(target)) throw ApiException.notFound("资源不存在");
        return target;
    }

    // ---------- 供项目模块复用的原始简历 HTML ----------

    public String readResumeHtml() {
        try {
            return Files.readString(Path.of(props.resumeHtml()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}

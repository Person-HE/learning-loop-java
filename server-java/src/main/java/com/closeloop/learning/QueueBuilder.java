package com.closeloop.learning;

import com.closeloop.common.Dates;
import com.closeloop.common.Utils;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.QueueItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 每日队列组装（机制规格 6.4）。优先级从高到低：
 * 同题复答 → 到期复习（项目技术优先 + 逾期×1 + 薄弱×2）→ 新知识点（三类权重最大余数法，类内按模块轮询）→ 薄弱缺口补强。
 * LC 题与「数据结构与算法」模块彻底分离：LC 复习只在 #/lc 独立模块内闭环，不进今日队列。
 * 锁定段（复答+到期）与权重分配段拆成独立方法，供交互式选题 PlanBuilder 复用。
 */
public final class QueueBuilder {

    private QueueBuilder() {}

    public static List<QueueItem> build(AppState st) {
        String today = Dates.todayStr();
        int maxQ = "minimal".equals(st.settings.mode) ? 1 : st.settings.maxQueue;
        Set<String> used = new HashSet<>();
        List<QueueItem> queue = lockedItems(st, maxQ, today, used);

        // 2. 新知识点：按三类权重分配题位（最大余数法），类内按模块（domain）轮询取题
        Map<String, List<String>> newByCat = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : weightAlloc(st, maxQ).entrySet()) {
            String c = e.getKey();
            List<String> domainOrder = domainOrder(st, c);
            List<String> ids = rotateNewKps(st, c, domainOrder, e.getValue());
            newByCat.put(c, ids);
        }

        List<String> catOrder = new ArrayList<>(newByCat.keySet());
        catOrder.sort(Comparator.comparingInt(c -> -weightOf(st, (String) c)));
        for (String c : catOrder) {
            for (String id : newByCat.getOrDefault(c, List.of())) {
                if (queue.size() >= maxQ) break;
                if (used.contains(id)) continue;
                used.add(id);
                Kp kp = st.kps.get(id);
                String domain = kp == null || kp.domain == null ? "" : kp.domain;
                queue.add(item(id, "new", categoryName(c) + "/" + domain + " · 新知识点", List.of()));
            }
        }

        // 3. 薄弱点补强（未解决缺口：项目技术优先 → 标签权重）
        weakItems(st, maxQ, queue, used);

        List<QueueItem> finalQ = queue.subList(0, Math.min(queue.size(), maxQ));
        st.queue = new ArrayList<>(finalQ);
        return st.queue;
    }

    /** 锁定段（0 同题复答 + 1 到期复习）：不受用户勾选影响，直接占位到 queue */
    static List<QueueItem> lockedItems(AppState st, int maxQ, String today, Set<String> used) {
        List<QueueItem> queue = new ArrayList<>();

        // 0. 同题复答：知识点内有「未掌握(<80) 且到期」的历史题 → 直接复用原题
        st.kps.values().stream()
                .filter(k -> !k.isLc && !"new".equals(k.state.status))
                .map(k -> new Object[]{k, replayQuestion(k, today), Priority.isProjectTech(k)})
                .filter(o -> ((Replay) o[1]) != null)
                .sorted(Comparator.comparing(o -> !((Boolean) o[2])))
                .forEach(o -> {
                    Kp k = (Kp) o[0];
                    Replay rp = (Replay) o[1];
                    if (queue.size() >= maxQ || used.contains(k.id)) return;
                    used.add(k.id);
                    queue.add(item(k.id, "review", "同题复答（上次 " + rp.score + " 分，未掌握）", List.of(rp.q)));
                });

        // 1. 到期复习卡
        String dd = today;
        st.kps.values().stream()
                .filter(k -> !k.isLc && !"new".equals(k.state.status) && Dates.cmpDate(k.state.due, dd) <= 0)
                .map(k -> new Object[]{k, Math.max(0, Dates.daysBetween(k.state.due, dd)) + (100 - (k.state.lastScore == null ? 50 : k.state.lastScore)) / 50.0, Priority.isProjectTech(k)})
                .sorted((a, b) -> {
                    int c = Boolean.compare(!((Boolean) a[2]), (Boolean) b[2]);
                    return c != 0 ? c : Double.compare((Double) b[1], (Double) a[1]);
                })
                .forEach(o -> {
                    Kp kp = (Kp) o[0];
                    if (queue.size() >= maxQ || used.contains(kp.id)) return;
                    used.add(kp.id);
                    Replay replay = replayQuestion(kp, today);
                    String reason = replay != null
                            ? "同题复答（上次 " + replay.score + " 分，未掌握）"
                            : "到期复习（逾期 " + Math.max(0, Dates.daysBetween(kp.state.due, today)) + " 天）";
                    queue.add(item(kp.id, "review", reason, replay != null ? List.of(replay.q) : List.of()));
                });

        return queue;
    }

    /** 三类权重 → 题位分配（最大余数法），仅含权重 >0 的类 */
    static LinkedHashMap<String, Integer> weightAlloc(AppState st, int maxQ) {
        List<String> cats = new ArrayList<>();
        for (String c : List.of("java", "algo", "ai")) {
            if (weightOf(st, c) > 0) cats.add(c);
        }
        double[] quotas = cats.stream().mapToDouble(c -> (weightOf(st, c) / 100.0) * maxQ).toArray();
        int[] alloc = new int[cats.size()];
        int used = 0;
        for (int i = 0; i < quotas.length; i++) {
            alloc[i] = (int) Math.floor(quotas[i]);
            used += alloc[i];
        }
        int rem = maxQ - used;
        Integer[] fracIdx = new Integer[cats.size()];
        for (int i = 0; i < cats.size(); i++) fracIdx[i] = i;
        java.util.Arrays.sort(fracIdx, (a, b) -> Double.compare(quotas[b] - Math.floor(quotas[b]), quotas[a] - Math.floor(quotas[a])));
        for (int k = 0; k < rem && fracIdx.length > 0; k++) alloc[fracIdx[k % fracIdx.length]]++;

        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < cats.size(); i++) out.put(cats.get(i), alloc[i]);
        return out;
    }

    /** 类内模块（domain）轮询取新知识点：最久未学的模块优先，模块内沿用原有排序规则 */
    static List<String> rotateNewKps(AppState st, String cat, List<String> domainOrder, int limit) {
        if (limit <= 0) return List.of();
        List<List<String>> pools = new ArrayList<>();
        for (String d : domainOrder) {
            List<String> pool = st.kps.values().stream()
                    .filter(k -> !k.isLc && cat.equals(k.category) && "new".equals(k.state.status)
                            && d.equals(k.domain == null ? "" : k.domain))
                    .map(k -> new Object[]{k, newScore(k)})
                    .sorted((a, b) -> {
                        int c1 = Integer.compare((Integer) a[1], (Integer) b[1]);
                        return c1 != 0 ? c1 : Integer.compare(((Kp) b[0]).hot, ((Kp) a[0]).hot);
                    })
                    .map(o -> ((Kp) o[0]).id)
                    .toList();
            if (!pool.isEmpty()) pools.add(pool);
        }
        List<String> picked = new ArrayList<>();
        int round = 0;
        while (picked.size() < limit) {
            boolean advanced = false;
            for (List<String> pool : pools) {
                if (round >= pool.size()) continue;
                picked.add(pool.get(round));
                advanced = true;
                if (picked.size() >= limit) break;
            }
            if (!advanced) break;
            round++;
        }
        return picked;
    }

    /** 模块轮询顺序：低优先级域（网络/云原生/计组/分布式）整体沉底；其余按「该模块最近学习日期」升序（从未学过最前），同名稳定序 */
    static List<String> domainOrder(AppState st, String cat) {
        Map<String, String> lastByDomain = new LinkedHashMap<>();
        for (Kp k : st.kps.values()) {
            if (k.isLc || !cat.equals(k.category)) continue;
            String d = k.domain == null ? "" : k.domain;
            String lr = k.state.lastReviewDate == null ? "" : k.state.lastReviewDate;
            lastByDomain.merge(d, lr, (a, b) -> Dates.cmpDate(a, b) >= 0 ? a : b);
        }
        List<String> domains = new ArrayList<>(lastByDomain.keySet());
        domains.sort(Comparator
                .comparing((String d) -> Priority.LOW_PRIORITY_DOMAINS.contains(d) ? 1 : 0)
                .thenComparing(lastByDomain::get)
                .thenComparing(Comparator.naturalOrder()));
        return domains;
    }

    /** 未解决缺口排序（项目技术优先 → 标签权重），build 与 PlanBuilder 共用 */
    static List<Gap> rankedWeakGaps(AppState st) {
        return st.gaps.stream()
                .filter(g -> g.resolvedAt == null)
                .sorted((a, b) -> {
                    boolean pa = st.kps.get(a.kpId) != null && Priority.isProjectTech(st.kps.get(a.kpId));
                    boolean pb = st.kps.get(b.kpId) != null && Priority.isProjectTech(st.kps.get(b.kpId));
                    int c = Boolean.compare(pb, pa);
                    return c != 0 ? c : Priority.gapLabelWeight(b.label) - Priority.gapLabelWeight(a.label);
                })
                .toList();
    }

    /** 薄弱缺口补强段：build 与 PlanBuilder 共用 */
    static void weakItems(AppState st, int maxQ, List<QueueItem> queue, Set<String> used) {
        for (Gap g : rankedWeakGaps(st)) {
            if (queue.size() >= maxQ || used.contains(g.kpId)) continue;
            Kp kp = st.kps.get(g.kpId);
            if (kp == null || kp.isLc) continue;
            used.add(g.kpId);
            queue.add(item(g.kpId, "weak", "薄弱点补强：" + g.label, List.of()));
        }
    }

    /** 新知识点类内排序分（越小越优先）：低优先级域 +100，非项目技术 +10 */
    private static int newScore(Kp k) {
        int s = 0;
        if (Priority.LOW_PRIORITY_DOMAINS.contains(k.domain)) s += 100;
        if (!Priority.isProjectTech(k)) s += 10;
        return s;
    }

    static int weightOf(AppState st, String cat) {
        return switch (cat) {
            case "java" -> st.settings.weights.java;
            case "algo" -> st.settings.weights.algo;
            case "ai" -> st.settings.weights.ai;
            default -> 0;
        };
    }

    public static String categoryName(String c) {
        return switch (c) {
            case "java" -> "Java后端";
            case "algo" -> "数据结构与算法";
            case "ai" -> "AI Agents";
            default -> c;
        };
    }

    /** 同题遗忘调度：找出「未掌握(<80) 且已到期」的历史题，按分数升序取最优 */
    static Replay replayQuestion(Kp kp, String today) {
        if (kp.state.questionHistory == null || kp.state.questionHistory.isEmpty()) return null;
        return kp.state.questionHistory.entrySet().stream()
                .filter(e -> {
                    Kp.QuestionHist h = e.getValue();
                    return h != null && h.score < 80 && h.due != null && Dates.cmpDate(h.due, today) <= 0
                            && h.question != null && !h.question.isEmpty();
                })
                .min(Comparator.comparingInt(e -> e.getValue().score))
                .map(e -> {
                    Kp.QuestionHist h = e.getValue();
                    QueueItem.Question q = new QueueItem.Question();
                    q.id = e.getKey();
                    q.type = "T2";
                    q.difficulty = 3;
                    q.question = h.question;
                    q.answerPoints = h.answerPoints != null ? h.answerPoints : new ArrayList<>();
                    q.anchor = "";
                    q.point = h.point != null ? h.point : "";
                    return new Replay(h.score, q);
                })
                .orElse(null);
    }

    record Replay(int score, QueueItem.Question q) {}

    static QueueItem item(String kpId, String kind, String reason, List<QueueItem.Question> qs) {
        QueueItem it = new QueueItem();
        it.kpId = kpId;
        it.kind = kind;
        it.reason = reason;
        it.qIdx = 0;
        it.questions = new ArrayList<>(qs);
        return it;
    }

    /** 队列对外脱敏视图（不含 answerPoints，防泄答案） */
    public static List<Map<String, Object>> sanitize(List<QueueItem> queue) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (QueueItem it : queue) {
            List<Map<String, Object>> qs = new ArrayList<>();
            for (QueueItem.Question q : it.questions) {
                qs.add(Utils.m(
                        "id", q.id, "type", q.type, "difficulty", q.difficulty,
                        "question", q.question, "anchor", q.anchor == null ? "" : q.anchor,
                        "point", q.point == null ? "" : q.point));
            }
            out.add(Utils.m(
                    "kpId", it.kpId, "kind", it.kind, "reason", it.reason, "qIdx", it.qIdx,
                    "genError", it.genError,
                    "questions", qs));
        }
        return out;
    }
}

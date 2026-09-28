package com.closeloop.learning;

import com.closeloop.common.Dates;
import com.closeloop.common.Utils;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.QueueItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 今日选题计划（交互式两步）：
 * plan    —— 只读预览：锁定卡（同题复答+到期复习）+ 各大类权重题位下的候选新知识点（按模块轮询）+ 薄弱补强建议；
 * confirm —— 用户勾选回传：锁定卡不砍、选题按勾选顺序砍到 maxQ，写回 st.queue。
 * 复用 QueueBuilder 拆出的 lockedItems / weightAlloc / domainOrder / rotateNewKps，保证预览与实际组装同规则。
 */
public final class PlanBuilder {

    private PlanBuilder() {}

    public static Map<String, Object> plan(AppState st) {
        String today = Dates.todayStr();
        int maxQ = "minimal".equals(st.settings.mode) ? 1 : st.settings.maxQueue;
        Set<String> used = new HashSet<>();
        List<QueueItem> locked = QueueBuilder.lockedItems(st, maxQ, today, used);

        List<Map<String, Object>> lockedRows = locked.stream()
                .map(it -> row(st, it.kpId, it.kind, it.reason, true))
                .toList();

        List<Map<String, Object>> catRows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : QueueBuilder.weightAlloc(st, maxQ).entrySet()) {
            String c = e.getKey();
            List<String> domainOrder = QueueBuilder.domainOrder(st, c);
            List<String> candIds = QueueBuilder.rotateNewKps(st, c, domainOrder, e.getValue());
            List<Map<String, Object>> candidates = candIds.stream()
                    .map(id -> row(st, id, "new",
                            QueueBuilder.categoryName(c) + "/" + domainOf(st, id) + " · 新知识点", false))
                    .toList();
            List<Map<String, Object>> modules = new ArrayList<>();
            for (String d : domainOrder) {
                List<Map<String, Object>> pool = newKpsInDomain(st, c, d).stream()
                        .map(k -> Utils.m(
                                "kpId", k.id, "chapter", k.chapter, "title", k.title,
                                "difficulty", k.difficulty, "hot", k.hot))
                        .toList();
                if (pool.isEmpty()) continue;
                modules.add(Utils.m("domain", d, "poolSize", pool.size(), "pool", pool));
            }
            catRows.add(Utils.m(
                    "cat", c, "catName", QueueBuilder.categoryName(c),
                    "weight", QueueBuilder.weightOf(st, c), "quota", e.getValue(),
                    "candidates", candidates, "modules", modules));
        }

        List<Map<String, Object>> weakRows = new ArrayList<>();
        for (Gap g : QueueBuilder.rankedWeakGaps(st)) {
            Kp kp = st.kps.get(g.kpId);
            if (kp == null || kp.isLc || used.contains(g.kpId)) continue;
            weakRows.add(row(st, g.kpId, "weak", "薄弱点补强：" + g.label, false));
        }

        return Utils.m(
                "maxQ", maxQ,
                "slots", Math.max(0, maxQ - locked.size()),
                "mode", st.settings.mode,
                "locked", lockedRows,
                "categories", catRows,
                "weak", weakRows);
    }

    /** 确认选题：锁定卡全保，picks 按序补足到 maxQ（超出的丢弃并回报 dropped） */
    public static Map<String, Object> confirm(AppState st, List<String> picks) {
        String today = Dates.todayStr();
        int maxQ = "minimal".equals(st.settings.mode) ? 1 : st.settings.maxQueue;
        Set<String> used = new HashSet<>();
        List<QueueItem> queue = QueueBuilder.lockedItems(st, maxQ, today, used);
        int lockedCount = queue.size();

        int dropped = 0;
        Set<String> seen = new HashSet<>();
        for (String id : picks == null ? List.<String>of() : picks) {
            if (id == null || !seen.add(id)) continue;
            Kp k = st.kps.get(id);
            if (k == null || k.isLc || used.contains(id)) continue;
            if (queue.size() >= maxQ) { dropped++; continue; }
            used.add(id);
            QueueItem it = QueueBuilder.item(id, "new",
                    QueueBuilder.categoryName(k.category) + "/" + domainOf(st, id) + " · 新知识点", List.of());
            if (!"new".equals(k.state.status)) {
                Gap open = firstOpenGap(st, id);
                if (open != null) {
                    it.kind = "weak";
                    it.reason = "薄弱点补强：" + open.label;
                } else {
                    it.kind = "review";
                    it.reason = "手动添加复习";
                }
            }
            queue.add(it);
        }

        st.queue = new ArrayList<>(queue);
        return Utils.m(
                "queue", QueueBuilder.sanitize(queue),
                "maxQ", maxQ,
                "lockedCount", lockedCount,
                "dropped", dropped);
    }

    // ---------- helpers ----------

    private static Map<String, Object> row(AppState st, String kpId, String kind, String reason, boolean locked) {
        Kp k = st.kps.get(kpId);
        return Utils.m(
                "kpId", kpId, "kind", kind, "reason", reason, "locked", locked,
                "category", k == null ? "" : k.category,
                "catName", k == null ? "" : QueueBuilder.categoryName(k.category),
                "domain", k == null ? "" : domainOf(st, kpId),
                "chapter", k == null ? "" : k.chapter,
                "title", k == null ? "" : k.title,
                "difficulty", k == null ? 3 : k.difficulty);
    }

    private static String domainOf(AppState st, String kpId) {
        Kp k = st.kps.get(kpId);
        return k == null || k.domain == null ? "" : k.domain;
    }

    private static List<Kp> newKpsInDomain(AppState st, String cat, String domain) {
        return st.kps.values().stream()
                .filter(k -> !k.isLc && cat.equals(k.category) && "new".equals(k.state.status)
                        && domain.equals(k.domain == null ? "" : k.domain))
                .toList();
    }

    private static Gap firstOpenGap(AppState st, String kpId) {
        return st.gaps.stream()
                .filter(g -> g.resolvedAt == null && kpId.equals(g.kpId))
                .findFirst().orElse(null);
    }
}

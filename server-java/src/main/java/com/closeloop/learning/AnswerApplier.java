package com.closeloop.learning;

import com.closeloop.common.Dates;
import com.closeloop.common.Utils;
import com.closeloop.state.model.AnswerRecord;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.QueueItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 作答后闭环回写（评分结果 → SM-2 调度 / 同题档案 / 今日会话 / 记录 / 缺口入库与消灭）。
 * outcome 采用评分路由产出的规范化 snake-key Map，保持与 Node 版存储形状一致。
 */
public final class AnswerApplier {

    private AnswerApplier() {}

    public record Result(String recordId, Kp.KpState kpState, AppState.SessionToday session) {}

    public static Result apply(AppState st, String kpId, String questionId, String question,
                               List<String> answerPoints, String point, String answerText,
                               Map<String, Object> outcome, Object stallMark, boolean spoken) {
        Kp kp = st.kps.get(kpId);
        if (kp == null) throw new IllegalStateException("知识点不存在: " + kpId);
        String today = Dates.todayStr();
        int totalScore = (int) Math.round(toNum(outcome.get("total_score")));
        String level = Utils.str(outcome.get("level"), "空白");

        kp.state = applySm2(kp.state, Sm2.levelToQ(level.isEmpty() ? "空白" : level), totalScore, today);
        kp.state.lastScore = totalScore;

        // 同题遗忘调度：每题档案（<80 分最快 2 天内复答；≥80 分 ≥7 天后长间隔）
        Map<String, Kp.QuestionHist> hist = kp.state.questionHistory;
        Kp.QuestionHist prev = hist.get(questionId);
        Kp.QuestionHist h = new Kp.QuestionHist();
        h.question = Utils.cut(question == null ? "" : question, 300);
        List<String> ap = prev != null && prev.answerPoints != null && !prev.answerPoints.isEmpty()
                ? prev.answerPoints : (answerPoints == null ? List.<String>of() : answerPoints);
        h.answerPoints = new ArrayList<>(ap.subList(0, Math.min(6, ap.size())));
        h.point = Utils.cut(point == null || point.isEmpty() ? (prev != null && prev.point != null ? prev.point : "") : point, 20);
        h.score = totalScore;
        h.tries = (prev != null ? prev.tries : 0) + 1;
        int interval = prev != null ? Math.max(1, (int) Math.round(prev.tries * 1.5)) : 3;
        h.due = totalScore >= 80 ? Dates.todayStr(Math.max(7, interval * 3)) : Dates.todayStr(Math.min(2, interval));
        h.lastAt = today;
        hist.put(questionId, h);

        AppState.SessionToday st2 = st.sessionToday;
        st2.count++;
        st2.avg = (int) Math.round((st2.avg * (st2.count - 1.0) + totalScore) / st2.count);
        st2.min += 4;
        if (spoken) st2.totalSpoken++;
        if (totalScore < 60) st2.newG++;
        AppState.DoneItem done = new AppState.DoneItem();
        done.kpId = kpId;
        done.questionId = questionId;
        done.score = totalScore;
        done.level = level;
        done.gaps = gapLabels(outcome.get("gaps"));
        st2.done.add(done);

        AnswerRecord rec = new AnswerRecord();
        rec.id = "AR-" + System.currentTimeMillis();
        rec.date = today;
        rec.kpId = kpId;
        rec.questionId = questionId;
        rec.question = Utils.cut(question, 500);
        rec.point = Utils.cut(point, 20);
        rec.answerPoints = new ArrayList<>(answerPoints == null ? List.of() : answerPoints.subList(0, Math.min(6, answerPoints == null ? 0 : answerPoints.size())));
        rec.answerText = Utils.cut(answerText, 3000);
        rec.totalScore = totalScore;
        rec.level = level;
        rec.verdict = Utils.str(outcome.get("verdict"), "");
        rec.profile = castMap(outcome.get("profile"));
        rec.coverageScore = (int) Math.round(toNum(outcome.get("coverage_score")));
        rec.scoreBreakdown = Utils.str(outcome.get("score_breakdown"), "");
        rec.standardPoints = castList(outcome.get("standard_points"));
        rec.pointCompare = castList(outcome.get("point_compare"));
        rec.feedback = castList(outcome.get("feedback"));
        rec.rewriteDiff = castList(outcome.get("rewrite_diff"));
        rec.optimizedAnswer = Utils.str(outcome.get("optimized_answer"), "");
        rec.dimensions = castList(outcome.get("dimensions"));
        rec.gaps = castList(outcome.get("gaps"));
        rec.stallMark = stallMark;
        rec.spoken = spoken;
        st.records.add(rec);
        if (st.records.size() > 5000) st.records.subList(0, st.records.size() - 5000).clear();

        // 缺口入库（同 kp 同标签未解决缺口去重 → 更新详情 + 复现时间）
        for (Map<String, Object> g : rec.gaps) {
            String label = Utils.str(g.get("label"), "");
            if (!Priority.GAP_LABEL_WEIGHT_KEYS.contains(label)) continue;
            String detail = Utils.str(g.get("detail"), "");
            Gap dup = st.gaps.stream()
                    .filter(x -> labelEquals(x, kpId, label) && x.resolvedAt == null)
                    .findFirst().orElse(null);
            if (dup != null) {
                if (!detail.isEmpty()) dup.detail = detail;
                dup.recurredAt = today;
            } else {
                Gap gap = new Gap();
                gap.id = "G-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
                gap.kpId = kpId;
                gap.title = kp.title;
                gap.label = label;
                gap.detail = detail;
                gap.createdAt = today;
                st.gaps.add(gap);
            }
        }

        // 消灭缺口：同知识点未解决缺口 + 本次 ≥75 分
        if (totalScore >= 75) {
            st.gaps.stream().filter(g -> g.kpId.equals(kpId) && g.resolvedAt == null).findFirst().ifPresent(open -> {
                open.resolvedAt = today;
                st2.resG++;
            });
        }

        return new Result(rec.id, kp.state, st2);
    }

    private static boolean labelEquals(Gap g, String kpId, String label) {
        return g.kpId.equals(kpId) && label.equals(g.label);
    }

    private static Kp.KpState applySm2(Kp.KpState s, int q, int totalScore, String today) {
        Sm2.update(s, q);
        Kp.HistoryEntry he = new Kp.HistoryEntry();
        he.date = today;
        he.score = totalScore;
        he.q = q;
        he.stability = s.stability;
        s.history.add(he);
        if (totalScore < 60) s.lapses++;
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object v) {
        return v instanceof List ? (List<Map<String, Object>>) v : new ArrayList<>();
    }

    private static List<String> gapLabels(Object gaps) {
        List<String> out = new ArrayList<>();
        if (gaps instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> map && map.get("label") != null) out.add(String.valueOf(map.get("label")));
            }
        }
        return out;
    }

    static double toNum(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return 0; }
    }

    /** 队列中已答题目移除（同知识点其余题仍可答）；队列项清空则整项移除 */
    public static void popAnsweredQuestion(AppState st, String kpId, String questionId) {
        QueueItem it = st.queue.stream().filter(x -> kpId.equals(x.kpId)).findFirst().orElse(null);
        if (it == null || it.questions == null || it.questions.isEmpty()) return;
        it.questions.removeIf(q -> q.id.equals(questionId));
        if (it.questions.isEmpty()) st.queue.removeIf(x -> kpId.equals(x.kpId));
    }

    /** 记录卡壳标记 */
    public static AnswerRecord markStall(AppState st, String recordId, Object mark) {
        AnswerRecord r = st.records.stream().filter(x -> x.id.equals(recordId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        r.stallMark = mark;
        return r;
    }
}

package com.closeloop.application.store;

import com.closeloop.infrastructure.persistence.entity.*;
import com.closeloop.infrastructure.persistence.mapper.*;
import com.closeloop.state.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 学习状态 MySQL 读写（MyBatis）。
 *
 * 问题：StateRepository 全量 JSON dumps+write ≈70ms/次（2026-09-23 实测）。
 * 决策：答题热路径只 UPSERT 变更行；设置类小 JSON 进 app_setting；启动从表重建 AppState。
 */
@Service
public class MySqlStateStore {

    private static final Logger log = LoggerFactory.getLogger(MySqlStateStore.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final KpMapper kpMapper;
    private final LearningMapper learningMapper;
    private final SupportMapper supportMapper;
    private final ObjectMapper om = new ObjectMapper();

    public MySqlStateStore(KpMapper kpMapper, LearningMapper learningMapper, SupportMapper supportMapper) {
        this.kpMapper = kpMapper;
        this.learningMapper = learningMapper;
        this.supportMapper = supportMapper;
    }

    public boolean isEmpty() {
        return kpMapper.countAll() == 0;
    }

    @Transactional
    public synchronized long importState(AppState st) {
        long t0 = System.nanoTime();
        for (Kp kp : st.kps.values()) saveKpAndState(kp);
        if (st.records != null) for (AnswerRecord r : st.records) saveRecord(r);
        if (st.gaps != null) for (Gap g : st.gaps) saveGap(g);
        if (st.sessions != null) {
            for (AppState.SessionDay d : st.sessions) {
                SessionDayEntity e = new SessionDayEntity();
                e.setSessionDate(parseDay(d.date));
                e.setCnt(d.count);
                e.setAvgScore(d.avg);
                e.setNewG(d.newG);
                e.setResG(d.resG);
                e.setMinScore(d.min);
                e.setSpoken(d.spoken);
                e.setMode(d.mode);
                supportMapper.upsertSessionDay(e);
            }
        }
        saveBlobs(st);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.info("[mysql-import] ms={} kps={} records={} gaps={}", ms, st.kps.size(),
                st.records == null ? 0 : st.records.size(), st.gaps == null ? 0 : st.gaps.size());
        return ms;
    }

    public Optional<AppState> load() {
        if (isEmpty()) return Optional.empty();
        AppState st = new AppState();
        st.settings = read(supportMapper.selectSetting("settings"), AppState.Settings.class, new AppState.Settings());
        st.cursor = readMapInt(supportMapper.selectSetting("cursor"), st.cursor);
        st.sessionToday = read(supportMapper.selectSetting("sessionToday"), AppState.SessionToday.class, new AppState.SessionToday());
        st.genSessions = readGen(supportMapper.selectSetting("genSessions"));
        st.gapTests = readGapTests(supportMapper.selectSetting("gapTests"));
        st.lcExplains = readLc(supportMapper.selectSetting("lcExplains"));
        st.project = read(supportMapper.selectSetting("project"), ProjectState.class, new ProjectState());

        st.kps = loadKps();
        st.records = loadRecords();
        st.gaps = loadGaps();
        Map<String, String> recurred = read(supportMapper.selectSetting("gapMeta"),
                new TypeReference<Map<String, String>>() {}, Map.of());
        for (Gap g : st.gaps) g.recurredAt = recurred.get(g.id);
        st.sessions = loadDays();
        st.queue = loadQueue();
        return Optional.of(st);
    }

    /** 答题热路径：只写变更行（history/question_hist 全删重插，保证任意调用次序下幂等） */
    @Transactional
    public long saveAfterAnswer(Kp kp, AnswerRecord record, List<Gap> newGaps) {
        long t0 = System.nanoTime();
        if (kp != null) saveKpAndState(kp);
        if (record != null) saveRecord(record);
        if (newGaps != null) for (Gap g : newGaps) saveGap(g);
        return (System.nanoTime() - t0) / 1_000_000;
    }

    public void saveBlobs(AppState st) {
        supportMapper.upsertSetting("settings", write(st.settings));
        supportMapper.upsertSetting("cursor", write(st.cursor));
        supportMapper.upsertSetting("sessionToday", write(st.sessionToday));
        supportMapper.upsertSetting("genSessions", write(st.genSessions));
        supportMapper.upsertSetting("gapTests", write(st.gapTests));
        supportMapper.upsertSetting("lcExplains", write(st.lcExplains));
        supportMapper.upsertSetting("project", write(st.project));
        // gap 表无 recurred_at 列（ll_app 无 DDL 权限）：复现日期属小 JSON，按本类决策进 app_setting
        Map<String, String> recurred = new LinkedHashMap<>();
        if (st.gaps != null) for (Gap g : st.gaps) if (g.recurredAt != null) recurred.put(g.id, g.recurredAt);
        supportMapper.upsertSetting("gapMeta", write(recurred));
        rewriteQueue(st);
    }

    /** 队列是内存态的镜像：每次 save 全量重写未消费行，重启后今日队列与题目不丢 */
    private void rewriteQueue(AppState st) {
        supportMapper.clearOpenQueue();
        if (st.queue == null || st.queue.isEmpty()) return;
        LocalDate d = st.sessionToday != null ? parseDay(st.sessionToday.date) : null;
        LocalDate day = d != null ? d : LocalDate.now();
        for (QueueItem item : st.queue) {
            QueueItemEntity e = new QueueItemEntity();
            e.setId(item.kpId + ":" + item.kind + ":" + UUID.randomUUID().toString().substring(0, 8));
            e.setKpId(item.kpId);
            e.setKind(item.kind);
            e.setReason(item.reason);
            e.setQuestionsJson(write(item.questions));
            e.setCreatedOn(day);
            supportMapper.insertQueue(e);
        }
    }

    public List<Map<String, Object>> trend14() {
        return learningMapper.selectTrend14();
    }

    private void saveKpAndState(Kp kp) {
        KpEntity ke = new KpEntity();
        ke.setId(kp.id);
        ke.setDomain(nz(kp.domain));
        ke.setChapter(nz(kp.chapter));
        ke.setTitle(nz(kp.title));
        ke.setDifficulty(kp.difficulty);
        ke.setHot(kp.hot);
        ke.setCategory(nz(kp.category));
        ke.setPath(nz(kp.path));
        ke.setKbStatus(nz(kp.kbStatus));
        ke.setIsLc(kp.isLc);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("prerequisites", kp.prerequisites);
        meta.put("diagrams", kp.diagrams);
        meta.put("assets", kp.assets);
        meta.put("anchors", kp.anchors);
        if (kp.lc != null) meta.put("lc", kp.lc);
        ke.setMetaJson(write(meta));
        kpMapper.upsert(ke);

        Kp.KpState s = kp.state == null ? new Kp.KpState() : kp.state;
        KpStateEntity se = new KpStateEntity();
        se.setKpId(kp.id);
        se.setStatus(nz(s.status));
        se.setEase(BigDecimal.valueOf(s.ease));
        se.setIntervalDays(s.interval);
        se.setDueDate(parseDay(s.due));
        se.setStability(BigDecimal.valueOf(s.stability));
        se.setLapses(s.lapses);
        se.setLastScore(s.lastScore);
        se.setReviews(s.reviews);
        se.setLastReviewDate(parseDay(s.lastReviewDate));
        se.setPlanJson(s.kpPlan == null ? null : write(s.kpPlan));
        kpMapper.upsertState(se);

        // 全删重插：热路径无论调用几次、顺序如何，落库结果都等于内存态
        kpMapper.deleteHistory(kp.id);
        if (s.history != null) {
            for (Kp.HistoryEntry h : s.history) {
                KpHistoryEntity he = new KpHistoryEntity();
                he.setKpId(kp.id);
                he.setReviewDate(parseDay(h.date));
                he.setScore(h.score);
                he.setQ(h.q);
                he.setStability(h.stability);
                kpMapper.insertHistory(he);
            }
        }
        learningMapper.deleteQuestionHists(kp.id);
        if (s.questionHistory != null) {
            for (var en : s.questionHistory.entrySet()) {
                learningMapper.upsertQuestionHist(toQh(kp.id, en.getKey(), en.getValue()));
            }
        }
    }

    private QuestionHistEntity toQh(String kpId, String qid, Kp.QuestionHist q) {
        QuestionHistEntity e = new QuestionHistEntity();
        e.setKpId(kpId);
        e.setQuestionId(qid);
        e.setQuestion(q.question);
        e.setAnswerPointsJson(write(q.answerPoints));
        e.setPoint(nz(q.point));
        e.setScore(q.score);
        e.setTries(q.tries);
        e.setDueDate(parseDay(q.due));
        e.setLastAt(parseDateTime(q.lastAt));
        return e;
    }

    private void saveRecord(AnswerRecord r) {
        AnswerRecordEntity e = new AnswerRecordEntity();
        e.setId(r.id);
        e.setKpId(r.kpId);
        e.setQuestionId(r.questionId);
        e.setQuestion(nz(r.question));
        e.setAnswerText(r.answerText);
        e.setSpoken(r.spoken);
        e.setScore(r.totalScore);
        e.setLevel(nz(r.level));
        e.setVerdict(nz(r.verdict));
        e.setProfile(r.profile == null ? null : write(r.profile));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("date", r.date);
        d.put("point", r.point);
        d.put("answerPoints", r.answerPoints);
        d.put("coverageScore", r.coverageScore);
        d.put("scoreBreakdown", r.scoreBreakdown);
        d.put("standardPoints", r.standardPoints);
        d.put("pointCompare", r.pointCompare);
        d.put("feedback", r.feedback);
        d.put("rewriteDiff", r.rewriteDiff);
        d.put("optimizedAnswer", r.optimizedAnswer);
        d.put("dimensions", r.dimensions);
        d.put("gaps", r.gaps);
        d.put("stallMark", r.stallMark);
        e.setDiagnoseJson(write(d));
        learningMapper.insertRecord(e);
    }

    private void saveGap(Gap g) {
        GapEntity e = new GapEntity();
        e.setId(g.id);
        e.setKpId(g.kpId);
        e.setLabel(nz(g.label));
        String desc = nz(g.title) + "\n\n" + nz(g.detail);
        e.setDescription(desc.length() > 500 ? desc.substring(0, 500) : desc);
        e.setCreatedAt(parseDateTime(g.createdAt));
        e.setResolvedAt(parseDay(g.resolvedAt));
        learningMapper.upsertGap(e);
    }

    private Map<String, Kp> loadKps() {
        Map<String, Kp> out = new LinkedHashMap<>();
        for (KpEntity ke : kpMapper.selectAll()) {
            Kp kp = new Kp();
            kp.id = ke.getId();
            kp.domain = ke.getDomain();
            kp.chapter = ke.getChapter();
            kp.title = ke.getTitle();
            kp.difficulty = ke.getDifficulty() == null ? 3 : ke.getDifficulty();
            kp.hot = ke.getHot() == null ? 3 : ke.getHot();
            kp.category = ke.getCategory();
            kp.path = ke.getPath();
            kp.kbStatus = ke.getKbStatus();
            kp.isLc = Boolean.TRUE.equals(ke.getIsLc());
            Map<String, Object> meta = read(ke.getMetaJson(), new TypeReference<Map<String, Object>>() {}, Map.of());
            kp.prerequisites = strList(meta.get("prerequisites"));
            kp.diagrams = strList(meta.get("diagrams"));
            kp.assets = mapList(meta.get("assets"));
            kp.anchors = mapList(meta.get("anchors"));
            @SuppressWarnings("unchecked")
            Map<String, Object> lc = (Map<String, Object>) meta.get("lc");
            kp.lc = lc;
            kp.state = new Kp.KpState();
            out.put(kp.id, kp);
        }
        for (KpStateEntity se : kpMapper.selectAllStates()) {
            Kp kp = out.get(se.getKpId());
            if (kp == null) continue;
            Kp.KpState s = new Kp.KpState();
            s.status = se.getStatus();
            s.ease = se.getEase() == null ? 2.5 : se.getEase().doubleValue();
            s.interval = se.getIntervalDays() == null ? 0 : se.getIntervalDays();
            s.due = se.getDueDate() == null ? "" : se.getDueDate().format(DAY);
            s.stability = se.getStability() == null ? 0 : se.getStability().doubleValue();
            s.lapses = se.getLapses() == null ? 0 : se.getLapses();
            s.lastScore = se.getLastScore();
            s.reviews = se.getReviews() == null ? 0 : se.getReviews();
            s.lastReviewDate = se.getLastReviewDate() == null ? null : se.getLastReviewDate().format(DAY);
            s.kpPlan = read(se.getPlanJson(), Kp.KpPlan.class, null);
            kp.state = s;
        }
        int qhRows = 0, qhEmpty = 0;
        for (Kp kp : out.values()) {
            kp.state.history = new ArrayList<>();
            for (KpHistoryEntity he : kpMapper.selectHistory(kp.id)) {
                Kp.HistoryEntry h = new Kp.HistoryEntry();
                h.date = he.getReviewDate() == null ? "" : he.getReviewDate().format(DAY);
                h.score = he.getScore() == null ? 0 : he.getScore();
                h.q = he.getQ() == null ? 0 : he.getQ();
                h.stability = he.getStability() == null ? 0 : he.getStability();
                kp.state.history.add(h);
            }
            kp.state.questionHistory = new LinkedHashMap<>();
            for (QuestionHistEntity qe : learningMapper.selectQuestionHists(kp.id)) {
                if (qe.getQuestionId() == null) continue; // null 键会让整个 /api/state 序列化失败
                Kp.QuestionHist q = new Kp.QuestionHist();
                q.question = qe.getQuestion();
                q.answerPoints = read(qe.getAnswerPointsJson(), new TypeReference<List<String>>() {}, List.of());
                q.point = qe.getPoint();
                q.score = qe.getScore() == null ? 0 : qe.getScore();
                q.tries = qe.getTries() == null ? 1 : qe.getTries();
                q.due = qe.getDueDate() == null ? "" : qe.getDueDate().format(DAY);
                q.lastAt = qe.getLastAt() == null ? null : qe.getLastAt().toString();
                kp.state.questionHistory.put(qe.getQuestionId(), q);
                qhRows++;
                if (q.answerPoints.isEmpty()) qhEmpty++;
            }
        }
        // 自检：answer_points 列没绑上时读出全空，且幂等重写会把空值写回库里（读坏→写腐的回路），必须出声
        if (qhRows > 0 && qhEmpty == qhRows) {
            log.warn("[store-selfcheck] question_hist 共 {} 行但 answerPoints 全空——疑似列映射失效（SELECT 缺 answer_points AS answer_points_json 别名），"
                    + "下次保存会把空数组回写库内", qhRows);
        }
        return out;
    }

    private List<AnswerRecord> loadRecords() {
        List<AnswerRecord> out = new ArrayList<>();
        for (AnswerRecordEntity e : learningMapper.selectAllRecords()) {
            AnswerRecord r = new AnswerRecord();
            r.id = e.getId();
            r.kpId = e.getKpId();
            r.questionId = e.getQuestionId();
            r.question = e.getQuestion();
            r.answerText = e.getAnswerText();
            r.totalScore = e.getScore() == null ? 0 : e.getScore();
            r.level = e.getLevel();
            r.verdict = e.getVerdict();
            r.spoken = Boolean.TRUE.equals(e.getSpoken());
            Map<String, Object> d = read(e.getDiagnoseJson(), new TypeReference<Map<String, Object>>() {}, Map.of());
            r.date = str(d.get("date"));
            r.point = str(d.get("point"));
            r.answerPoints = strList(d.get("answerPoints"));
            r.coverageScore = num(d.get("coverageScore"));
            r.scoreBreakdown = str(d.get("scoreBreakdown"));
            r.standardPoints = mapList(d.get("standardPoints"));
            r.pointCompare = mapList(d.get("pointCompare"));
            r.feedback = mapList(d.get("feedback"));
            r.rewriteDiff = mapList(d.get("rewriteDiff"));
            r.optimizedAnswer = str(d.get("optimizedAnswer"));
            r.dimensions = mapList(d.get("dimensions"));
            r.gaps = mapList(d.get("gaps"));
            r.stallMark = d.get("stallMark");
            out.add(r);
        }
        return out;
    }

    private List<Gap> loadGaps() {
        List<Gap> out = new ArrayList<>();
        for (GapEntity e : learningMapper.selectAllGaps()) {
            Gap g = new Gap();
            g.id = e.getId();
            g.kpId = e.getKpId();
            g.label = e.getLabel();
            String desc = e.getDescription();
            if (desc != null && desc.contains("\n\n")) {
                int p = desc.indexOf("\n\n");
                g.title = desc.substring(0, p);
                g.detail = desc.substring(p + 2);
            } else {
                g.title = desc;
            }
            g.createdAt = e.getCreatedAt() == null ? null : e.getCreatedAt().toString();
            g.resolvedAt = e.getResolvedAt() == null ? null : e.getResolvedAt().format(DAY);
            out.add(g);
        }
        return out;
    }

    private List<AppState.SessionDay> loadDays() {
        List<AppState.SessionDay> out = new ArrayList<>();
        for (SessionDayEntity e : supportMapper.selectAllSessionDays()) {
            AppState.SessionDay d = new AppState.SessionDay();
            d.date = e.getSessionDate() == null ? "" : e.getSessionDate().format(DAY);
            d.count = e.getCnt() == null ? 0 : e.getCnt();
            d.avg = e.getAvgScore() == null ? 0 : e.getAvgScore();
            d.newG = e.getNewG() == null ? 0 : e.getNewG();
            d.resG = e.getResG() == null ? 0 : e.getResG();
            d.min = e.getMinScore() == null ? 0 : e.getMinScore();
            d.spoken = Boolean.TRUE.equals(e.getSpoken());
            d.mode = e.getMode();
            out.add(d);
        }
        return out;
    }

    private List<QueueItem> loadQueue() {
        List<QueueItem> out = new ArrayList<>();
        for (QueueItemEntity e : supportMapper.selectOpenQueue()) {
            QueueItem item = new QueueItem();
            item.kpId = e.getKpId();
            item.kind = e.getKind();
            item.reason = e.getReason();
            item.questions = read(e.getQuestionsJson(), new TypeReference<List<QueueItem.Question>>() {}, List.of());
            out.add(item);
        }
        // 自检：库里行非空但全部题目为空 = 列没绑定上（questions/questionsJson 前科），静默失效唯一可见出口
        if (!out.isEmpty() && out.stream().allMatch(i -> i.questions == null || i.questions.isEmpty())) {
            log.warn("[store-selfcheck] queue_item 有 {} 行但题目全空——疑似列映射失效（SELECT 缺 questions AS questions_json 别名），"
                    + "前端将误触全量重新出题", out.size());
        }
        return out;
    }

    private String write(Object o) {
        try { return om.writeValueAsString(o); } catch (Exception ex) { return "null"; }
    }

    private <T> T read(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try { return om.readValue(json, type); } catch (Exception e) {
            log.warn("[store-read] {} 列 JSON 解析失败，回退默认值（数据可能失真）：{}", type.getSimpleName(), e.getMessage());
            return fallback;
        }
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try { return om.readValue(json, type); } catch (Exception e) {
            log.warn("[store-read] {} 列 JSON 解析失败，回退默认值（数据可能失真）：{}", type, e.getMessage());
            return fallback;
        }
    }

    private Map<String, Integer> readMapInt(String json, Map<String, Integer> fallback) {
        return read(json, new TypeReference<Map<String, Integer>>() {}, fallback);
    }

    private LinkedHashMap<String, AppState.GenSession> readGen(String json) {
        return read(json, new TypeReference<LinkedHashMap<String, AppState.GenSession>>() {}, new LinkedHashMap<>());
    }

    private LinkedHashMap<String, QueueItem.Question> readGapTests(String json) {
        return read(json, new TypeReference<LinkedHashMap<String, QueueItem.Question>>() {}, new LinkedHashMap<>());
    }

    private LinkedHashMap<String, LcExplain> readLc(String json) {
        return read(json, new TypeReference<LinkedHashMap<String, LcExplain>>() {}, new LinkedHashMap<>());
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static int num(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) if (x instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    private static LocalDate parseDay(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s); } catch (Exception e) { return null; }
    }

    private static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return LocalDateTime.now();
        try {
            if (s.length() == 10) return LocalDate.parse(s).atStartOfDay();
            return LocalDateTime.parse(s.replace('T', ' ').length() > 19 ? s.replace('T', ' ').substring(0, 19) : s.replace('T', ' '));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}

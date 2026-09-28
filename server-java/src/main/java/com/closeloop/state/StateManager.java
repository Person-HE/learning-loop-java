package com.closeloop.state;

import com.closeloop.application.store.MySqlStateStore;
import com.closeloop.common.Dates;
import com.closeloop.config.AppProperties;
import com.closeloop.knowledge.KbService;
import com.closeloop.knowledge.KnowledgeBase;
import com.closeloop.knowledge.LcDataService;
import com.closeloop.state.model.AnswerRecord;
import com.closeloop.state.model.AppState;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 全局状态管理器。
 * mysql 模式：启动导入/加载走 MyBatis；答题热路径 saveHot 只写变更行。
 */
@Component
public class StateManager {

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);

    private final StateRepository repo;
    private final KbService kbService;
    private final LcDataService lcDataService;
    private final MySqlStateStore mysqlStore;
    private final AppProperties props;

    private AppState st;
    private final ReentrantLock genLock = new ReentrantLock();
    private final Object guard = new Object();

    public StateManager(StateRepository repo, KbService kbService, LcDataService lcDataService,
                        MySqlStateStore mysqlStore, AppProperties props) {
        this.repo = repo;
        this.kbService = kbService;
        this.lcDataService = lcDataService;
        this.mysqlStore = mysqlStore;
        this.props = props;
    }

    @PostConstruct
    void init() {
        KnowledgeBase kb = kbService.getKb(false);
        AppState loaded;
        if (props.mysqlMode()) {
            if (mysqlStore.isEmpty()) {
                AppState fromJson = repo.load();
                if (fromJson != null) {
                    normalize(fromJson);
                    long ms = mysqlStore.importState(fromJson);
                    log.info("[state] state.json → MySQL 导入完成, {}ms", ms);
                }
            }
            loaded = mysqlStore.load().orElse(null);
        } else {
            loaded = repo.load();
        }
        if (loaded == null) {
            st = initState(kb);
        } else {
            st = loaded;
            normalize(st);
        }
        mergeKbKps(st, kb);
        rollover(st);
        save();
        log.info("[state] 加载完成：kp={} records={} storage={}", st.kps.size(), st.records.size(),
                props.mysqlMode() ? "mysql" : "json");
    }

    public AppState state() { return st; }

    public void save() {
        if (props.mysqlMode()) {
            mysqlStore.saveBlobs(st);
            return;
        }
        repo.save(st);
    }

    /** 答题热路径：只写变更行 */
    public long saveHot(Kp kp, AnswerRecord record, List<Gap> newGaps) {
        if (props.mysqlMode()) {
            return mysqlStore.saveAfterAnswer(kp, record, newGaps);
        }
        long t0 = System.nanoTime();
        repo.save(st);
        return (System.nanoTime() - t0) / 1_000_000;
    }

    public <T> T mutate(Supplier<T> fn) {
        synchronized (guard) {
            T r = fn.get();
            save();
            return r;
        }
    }

    public void mutateVoid(Runnable fn) {
        synchronized (guard) {
            fn.run();
            save();
        }
    }

    public <T> T withGenLock(Supplier<T> fn) {
        if (genLock.isLocked()) throw com.closeloop.common.ApiException.badRequest("正在生成题目，请稍候…");
        genLock.lock();
        try {
            return fn.get();
        } finally {
            genLock.unlock();
        }
    }

    static void normalize(AppState st) {
        if (st.genSessions == null) st.genSessions = new LinkedHashMap<>();
        if (st.gapTests == null) st.gapTests = new LinkedHashMap<>();
        if (st.lcExplains == null) st.lcExplains = new LinkedHashMap<>();
        if (st.project == null) st.project = new com.closeloop.state.model.ProjectState();
        if (st.kps == null) st.kps = new LinkedHashMap<>();
        if (st.queue == null) st.queue = new ArrayList<>();
        if (st.gaps == null) st.gaps = new ArrayList<>();
        if (st.records == null) st.records = new ArrayList<>();
        if (st.sessions == null) st.sessions = new ArrayList<>();
        if (st.sessionToday == null) st.sessionToday = new AppState.SessionToday();
        if (st.settings == null) st.settings = new AppState.Settings();
        if (st.settings.weights == null) st.settings.weights = new AppState.Weights();
        for (Kp kp : st.kps.values()) {
            if (kp.state == null) kp.state = freshKpState();
            if (kp.state.questionHistory == null) kp.state.questionHistory = new LinkedHashMap<>();
            if (kp.state.history == null) kp.state.history = new ArrayList<>();
        }
    }

    AppState initState(KnowledgeBase kb) {
        AppState st = new AppState();
        st.settings.firstUseDate = Dates.todayStr();
        for (Kp kp : kb.kps) {
            kp.state = freshKpState();
            st.kps.put(kp.id, kp);
        }
        for (Kp kp : lcDataService.buildLcKps().values()) {
            kp.state = freshKpState();
            st.kps.putIfAbsent(kp.id, kp);
        }
        st.sessionToday.date = Dates.todayStr();
        return st;
    }

    public static Kp.KpState freshKpState() {
        return new Kp.KpState();
    }

    public int mergeKbKps(AppState st, KnowledgeBase kb) {
        int added = 0;
        Map<String, Kp> lcKps = lcDataService.buildLcKps();
        for (Map.Entry<String, Kp> e : lcKps.entrySet()) {
            Kp existing = st.kps.get(e.getKey());
            if (existing == null) {
                Kp kp = e.getValue();
                kp.state = freshKpState();
                st.kps.put(e.getKey(), kp);
                added++;
            } else {
                Kp fresh = e.getValue();
                fresh.state = existing.state;
                st.kps.put(e.getKey(), fresh);
            }
        }
        for (Kp kp : kb.kps) {
            Kp existing = st.kps.get(kp.id);
            if (existing == null) {
                kp.state = freshKpState();
                st.kps.put(kp.id, kp);
                added++;
            } else {
                kp.state = existing.state;
                st.kps.put(kp.id, kp);
            }
        }
        return added;
    }

    public void rollover(AppState st) {
        String today = Dates.todayStr();
        if (today.equals(st.sessionToday.date)) return;
        AppState.SessionToday prev = st.sessionToday;
        if (prev.count > 0) {
            AppState.SessionDay day = new AppState.SessionDay();
            day.date = prev.date;
            day.count = prev.count;
            day.avg = prev.avg;
            day.newG = prev.newG;
            day.resG = prev.resG;
            day.min = prev.min;
            day.spoken = prev.totalSpoken == prev.count;
            day.mode = st.settings.mode;
            st.sessions.add(day);
            st.settings.missedDays = 0;
            if ("minimal".equals(st.settings.mode)) {
                st.settings.minimalStreak++;
                if (st.settings.minimalStreak >= 3) {
                    st.settings.mode = "standard";
                    st.settings.minimalStreak = 0;
                }
            }
        } else {
            int missed = Math.max(1, Dates.daysBetween(prev.date, today));
            st.settings.missedDays = Math.max(0, st.settings.missedDays) + missed;
            if (st.settings.missedDays >= 3 && "standard".equals(st.settings.mode)) {
                st.settings.mode = "minimal";
                st.settings.minimalStreak = 0;
            }
        }
        AppState.SessionToday fresh = new AppState.SessionToday();
        fresh.date = today;
        st.sessionToday = fresh;
        st.settings.frozen = Dates.daysBetween(st.settings.firstUseDate, today) < st.settings.freezeDays;
        save();
    }

    public void ensureToday() {
        synchronized (guard) {
            if (!Dates.todayStr().equals(st.sessionToday.date)) {
                rollover(st);
            }
        }
    }

    public void reset(KnowledgeBase kb) {
        synchronized (guard) {
            AppState fresh = new AppState();
            fresh.settings = st.settings;
            fresh.settings.firstUseDate = Dates.todayStr();
            fresh.settings.missedDays = 0;
            fresh.settings.minimalStreak = 0;
            fresh.settings.mode = "standard";
            fresh.project = st.project;
            mergeKbKps(fresh, kb);
            fresh.sessionToday.date = Dates.todayStr();
            st = fresh;
            save();
            if (props.mysqlMode()) {
                for (Kp kp : st.kps.values()) mysqlStore.saveAfterAnswer(kp, null, null);
            }
        }
    }

    public LocalDate localToday() { return LocalDate.now(); }
}

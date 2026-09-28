package com.closeloop.state.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局学习状态（与 server/data/state.json v2 完全兼容，Node/Java 两套后端可共享同一数据文件）。
 * 采用公开字段的数据载体风格：本类只是持久化 DTO，业务规则全部在 learning 包内。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppState {

    public int version = 2;
    public Settings settings = new Settings();
    /** 知识点 id → 知识点（含学习状态），有序 */
    public Map<String, Kp> kps = new LinkedHashMap<>();
    public Map<String, Integer> cursor = new LinkedHashMap<>(Map.of("java", 0, "ai", 0, "algo", 0));
    public List<QueueItem> queue = new ArrayList<>();
    /** 地图直接出题的临时会话：kpId → {questions, ts}，24h 过期清理 */
    public Map<String, GenSession> genSessions = new LinkedHashMap<>();
    /** 缺口检测题：questionId → question（一次性，评分取走即删） */
    public Map<String, QueueItem.Question> gapTests = new LinkedHashMap<>();
    /** LC 精讲 AI 缓存：题号 → 精讲 */
    public Map<String, LcExplain> lcExplains = new LinkedHashMap<>();
    public SessionToday sessionToday = new SessionToday();
    public List<SessionDay> sessions = new ArrayList<>();
    public List<Gap> gaps = new ArrayList<>();
    public List<AnswerRecord> records = new ArrayList<>();
    public ProjectState project = new ProjectState();
    /** 持久化占位（每次响应实时重算） */
    public Map<String, Object> kpi = new LinkedHashMap<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Settings {
        public String apiKey = "";
        public String baseUrl = "";
        public String model = "";
        public Weights weights = new Weights();
        public String firstUseDate = "";
        public int freezeDays = 14;
        public boolean frozen = true;
        public String mode = "standard"; // standard | minimal
        public int missedDays = 0;
        public int minimalStreak = 0;
        public int maxQueue = 5;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Weights {
        public int java = 40;
        public int algo = 30;
        public int ai = 30;

        public Weights() {}
        public Weights(int java, int algo, int ai) {
            this.java = java; this.algo = algo; this.ai = ai;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenSession {
        public List<QueueItem.Question> questions = new ArrayList<>();
        public long ts;
    }

    /** 今日会话计数（sessionToday.done 记录每题摘要） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SessionToday {
        public String date = "";
        public int count = 0;
        public int avg = 0;
        public int newG = 0;
        public int resG = 0;
        public int min = 0;
        public int totalSpoken = 0;
        public List<DoneItem> done = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DoneItem {
        public String kpId;
        public String questionId;
        public int score;
        public String level;
        public List<String> gaps = new ArrayList<>();
    }

    /** 历史日会话（rollover 归档） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SessionDay {
        public String date;
        public int count;
        public int avg;
        public int newG;
        public int resG;
        public int min;
        public boolean spoken;
        public String mode;
    }
}

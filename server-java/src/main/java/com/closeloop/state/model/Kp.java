package com.closeloop.state.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 知识点：知识库元信息 + 学习状态（SM-2 调度数据）。持久化 DTO，无行为。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Kp {

    public String id;
    public String domain;
    public String chapter;
    public String title;
    public int difficulty = 3;
    public int hot = 3;
    public List<String> prerequisites = new ArrayList<>();
    public List<String> diagrams = new ArrayList<>();
    public List<Map<String, Object>> assets = new ArrayList<>();
    /** 知识库文档相对路径（KB 根目录下） */
    public String path = "";
    public List<Map<String, Object>> anchors = new ArrayList<>();
    /** done | lc（力扣知识点） */
    public String kbStatus = "done";
    /** java | algo | ai */
    public String category = "java";
    public boolean isLc = false;
    /** LC 知识点附题目引用：{no,title,algo,category,url} */
    public Map<String, Object> lc;
    public KpState state = new KpState();

    /** 知识点学习状态（SM-2 + FSRS 双因子记忆模型数据） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KpState {
        /** new | learning | relearning | mastered */
        public String status = "new";
        public double ease = 2.5;
        public int interval = 0;
        public String due = "";
        /** 记忆稳定性 S（R = e^(-Δt/S)） */
        public double stability = 0;
        public int lapses = 0;
        public Integer lastScore;
        public int reviews = 0;
        public List<HistoryEntry> history = new ArrayList<>();
        public String lastReviewDate;
        /** 考点清单 + 记忆卡（AI 懒生成缓存） */
        public KpPlan kpPlan;
        /** 同题遗忘调度：questionId → 该题掌握档案 */
        public Map<String, QuestionHist> questionHistory = new LinkedHashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistoryEntry {
        public String date;
        public int score;
        public int q;
        public double stability;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionHist {
        public String question;
        public List<String> answerPoints = new ArrayList<>();
        public String point = "";
        public int score;
        public int tries;
        public String due;
        public String lastAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KpPlan {
        public List<Map<String, Object>> points = new ArrayList<>();
        public List<Map<String, Object>> memory_cards = new ArrayList<>();
        /** 该知识点下已答过且 ≥75 分的考点名 */
        public List<String> resolvedPoints = new ArrayList<>();
    }
}

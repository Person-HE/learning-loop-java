package com.closeloop.state.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 作答记录（一次「出题 → 作答 → AI 评分」的完整凭证）。
 * AI 结构化产物（standardPoints/dimensions/…）以透传 Map 存储，与 Node 版 JSON 形状逐字段一致。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnswerRecord {

    public String id;
    public String date;
    public String kpId;
    public String questionId;
    public String question;
    public String point;
    public List<String> answerPoints = new ArrayList<>();
    public String answerText;
    public int totalScore;
    public String level;
    public String verdict;
    public Map<String, Object> profile;
    public int coverageScore;
    public String scoreBreakdown;
    public List<Map<String, Object>> standardPoints = new ArrayList<>();
    public List<Map<String, Object>> pointCompare = new ArrayList<>();
    public List<Map<String, Object>> feedback = new ArrayList<>();
    public List<Map<String, Object>> rewriteDiff = new ArrayList<>();
    public String optimizedAnswer;
    public List<Map<String, Object>> dimensions = new ArrayList<>();
    public List<Map<String, Object>> gaps = new ArrayList<>();
    /** 卡壳标记：blank | term | wrong | null */
    public Object stallMark;
    public boolean spoken;
}

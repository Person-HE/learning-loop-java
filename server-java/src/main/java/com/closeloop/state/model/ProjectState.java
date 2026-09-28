package com.closeloop.state.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 项目部分状态：切面复习调度 / 面试会话 / 简历评分缓存 / 深挖题缓存 / 模拟面试进行时会话 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectState {

    public Map<String, FacetState> facets = new LinkedHashMap<>();
    /** 面试会话（PS- 深挖 / MS- 模拟），形状按来源不同，透传存储 */
    public List<Map<String, Object>> sessions = new ArrayList<>();
    public Map<String, Object> resumeScore;
    public Cache cache;
    public Mock mock;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FacetState {
        public int reviews;
        public Integer lastScore;
        public String lastLevel;
        public String due;
        /** [{date,score,level,kind?}] */
        public List<Map<String, Object>> history = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cache {
        public String facetId;
        public long ts;
        /** 项目深挖题（含 answerPoints/followUps，不下发要点以外内容按路由约定） */
        public List<Map<String, Object>> questions = new ArrayList<>();
    }

    /** 进行中的模拟面试会话 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mock {
        public String facetId;
        /** [{role: interviewer|candidate, content}] */
        public List<Map<String, Object>> history = new ArrayList<>();
        public int qCount;
        public int maxQuestions;
        public long startedAt;
    }
}

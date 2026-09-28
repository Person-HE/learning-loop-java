package com.closeloop.state.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** 今日队列项：review（到期复习/同题复答）| new（新知识点）| weak（薄弱补强）| gaptest | lc | mock */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueueItem {

    public String kpId;
    public String kind;
    public String reason;
    public int qIdx;
    public String genError;
    public List<Question> questions = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Question {
        public String id;
        public String kpId;
        /** T1~T6 / CODE */
        public String type = "T2";
        public int difficulty = 3;
        public String question = "";
        /** 标准评分要点（不下发给前端） */
        public List<String> answerPoints = new ArrayList<>();
        public String anchor = "";
        /** ≤12 字考点名 */
        public String point = "";
        /** 缺口检测题关联的缺口 id */
        public String gapId;
    }
}

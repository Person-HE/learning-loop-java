package com.closeloop.knowledge;

import com.closeloop.config.AppProperties;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.LcExplain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 力扣 100 题库数据层：lc100.json 题面 + lc-solutions.json 内置精讲 + LC 知识点注册 */
@Component
public class LcDataService {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LcProblem {
        public int no;
        public String title;
        public String slug;
        /** 1 简单 / 2 中等 / 3 困难 */
        public int d = 1;
        public String category;
        public String algo;
        public String q;
        public List<Object> ex = new ArrayList<>();
        public String c;
        public String url;

        public String leetcodeUrl() {
            return url != null && !url.isEmpty() ? url : "https://leetcode.cn/problems/" + slug + "/";
        }

        public String dName() {
            return switch (d) { case 1 -> "简单"; case 2 -> "中等"; case 3 -> "困难"; default -> ""; };
        }
    }

    private static class LcFile { public List<LcProblem> problems = new ArrayList<>(); }

    private static class LcSolFile { public Map<String, LcExplain> solutions = new LinkedHashMap<>(); }

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private volatile List<LcProblem> problems;
    private volatile Map<String, LcExplain> solutions;

    public LcDataService(AppProperties props) {
        this.props = props;
    }

    public List<LcProblem> getProblems() {
        List<LcProblem> p = problems;
        if (p != null) return p;
        synchronized (this) {
            if (problems != null) return problems;
            try {
                LcFile f = mapper.readValue(Files.readString(props.dataPath().resolve("lc100.json")).getBytes("UTF-8"), LcFile.class);
                problems = f.problems != null ? f.problems : new ArrayList<>();
            } catch (IOException | RuntimeException e) {
                problems = new ArrayList<>();
            }
            return problems;
        }
    }

    public Map<String, LcExplain> getSolutions() {
        Map<String, LcExplain> s = solutions;
        if (s != null) return s;
        synchronized (this) {
            if (solutions != null) return solutions;
            try {
                LcSolFile f = mapper.readValue(Files.readString(props.dataPath().resolve("lc-solutions.json")).getBytes("UTF-8"), LcSolFile.class);
                solutions = f.solutions != null ? f.solutions : new LinkedHashMap<>();
            } catch (IOException | RuntimeException e) {
                solutions = new LinkedHashMap<>();
            }
            return solutions;
        }
    }

    public LcProblem byNo(String no) {
        return getProblems().stream().filter(p -> String.valueOf(p.no).equals(no)).findFirst().orElse(null);
    }

    /** LC 题注册为系统知识点：lc-{no}，category=algo，isLc=true 排除出新知识点分配 */
    public Map<String, Kp> buildLcKps() {
        Map<String, Kp> map = new LinkedHashMap<>();
        for (LcProblem p : getProblems()) {
            Kp kp = new Kp();
            kp.id = "lc-" + p.no;
            kp.domain = "leetcode";
            kp.chapter = "LC100";
            kp.title = p.no + ". " + p.title;
            kp.category = "algo";
            kp.difficulty = p.d;
            kp.hot = p.d == 1 ? 5 : p.d == 2 ? 4 : 3;
            kp.path = "";
            kp.kbStatus = "lc";
            kp.isLc = true;
            kp.lc = com.closeloop.common.Utils.m(
                    "no", p.no, "title", p.title, "algo", p.algo, "category", p.category,
                    "url", p.leetcodeUrl());
            map.put(kp.id, kp);
        }
        return map;
    }
}

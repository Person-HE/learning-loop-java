package com.closeloop.knowledge;

import com.closeloop.state.model.Kp;

import java.util.List;
import java.util.Map;

/** 知识库快照：域统计 + 知识点注册表（只读层输出） */
public class KnowledgeBase {

    public String root;
    public boolean exists;
    /** [{name,category,docs,status}] */
    public List<Map<String, Object>> domains = List.of();
    public int totalDocs;
    /** 注册表（仅元信息，state 由 StateManager 合并时补齐） */
    public List<Kp> kps = List.of();
    public Map<String, List<Map<String, String>>> plannedPaths = KbScanner.PLANNED_PATHS;
}

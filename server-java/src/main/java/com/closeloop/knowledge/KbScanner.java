package com.closeloop.knowledge;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识库扫描器（纯静态工具，无 IO 副作用以外的状态）：
 * 目录遍历 → YAML frontmatter 解析 → H2/H3 锚点提取 → 域分类 → 知识点注册表。
 */
public final class KbScanner {

    private KbScanner() {}

    private static final Pattern CAT_JAVA = Pattern.compile(
            "java|jvm|concurrent|并发|mysql|redis|mq|spring|network|网络|os|操作系统|comput|计组|distributed|分布式|system|设计|cloud|云|netty|性能|middleware|中间件|interview|面|glossary|项目|scenario|场景",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CAT_AI = Pattern.compile(
            "(^|[-_\\s])ai([-_\\s]|$)|agent|llm|rag|langchain|大模型|prompt|embedding|向量|mcp|微调",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CAT_ALGO = Pattern.compile(
            "algo|算法|leetcode|数据结构|data[-_\\s]?structure", Pattern.CASE_INSENSITIVE);

    /** 学习路径（机制规格第八章）：控制「下一个新知识点」顺序 */
    public static final Map<String, List<Map<String, String>>> PLANNED_PATHS = plannedPaths();

    private static Map<String, List<Map<String, String>>> plannedPaths() {
        Map<String, List<Map<String, String>>> m = new LinkedHashMap<>();
        m.put("java", List.of(
                domain("comput-arch", "计算机组成原理"), domain("os", "操作系统"),
                domain("network", "计算机网络"), domain("netty", "Netty 与 NIO"),
                domain("java", "Java 基础"), domain("jvm", "JVM"),
                domain("concurrent", "Java 并发"), domain("mysql", "MySQL"),
                domain("redis", "Redis"), domain("mq", "消息队列"),
                domain("spring", "Spring 全家桶"), domain("distributed", "分布式系统"),
                domain("system-design", "系统设计"), domain("cloud-native", "云原生与 DevOps")));
        m.put("ai", List.of(domain("ai", "AI Agents（LLM/Embedding/Prompt/RAG/Agent/Function Calling/MCP/记忆/向量库/LangChain4j/微调部署）")));
        m.put("algo", List.of(domain("algorithm", "数据结构与算法（复杂度/数组/链表/栈队/哈希/树/堆/图/排序/查找/DP/贪心回溯/LeetCode100）")));
        return m;
    }

    private static Map<String, String> domain(String d, String name) {
        return Map.of("domain", d, "name", name);
    }

    public static String classifyDomain(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (CAT_ALGO.matcher(n).find()) return "algo";
        if (CAT_AI.matcher(n).find()) return "ai";
        if (CAT_JAVA.matcher(n).find()) return "java";
        return "java";
    }

    /** 解析 frontmatter：difficulty(★数)/interview_hot/prerequisites/diagrams 等 */
    public static Map<String, Object> parseFrontmatter(String text) {
        Map<String, Object> meta = new LinkedHashMap<>();
        Matcher m = Pattern.compile("^---\\r?\\n([\\s\\S]*?)\\r?\\n---").matcher(text);
        if (!m.find()) return meta;
        for (String line : m.group(1).split("\\r?\\n")) {
            Matcher kv = Pattern.compile("^\\s*([A-Za-z_]+)\\s*:\\s*(.*)$").matcher(line);
            if (!kv.matches()) continue;
            String k = kv.group(1);
            String v = kv.group(2).trim().replaceAll("^[\"']|[\"']$", "");
            switch (k) {
                case "difficulty" -> {
                    int stars = v.length() - v.replace("★", "").length();
                    int parsed = parseIntSafe(v, 0);
                    meta.put(k, stars > 0 ? stars : (parsed > 0 ? parsed : 3));
                }
                case "interview_hot" -> {
                    int parsed = parseIntSafe(v, 0);
                    meta.put(k, parsed > 0 ? parsed : 3);
                }
                case "prerequisites", "diagrams", "sources" -> {
                    List<String> list = new ArrayList<>();
                    for (String s : v.replaceAll("[\\[\"]", "").split(",")) {
                        String t = s.trim();
                        if (!t.isEmpty()) list.add(t);
                    }
                    meta.put(k, list);
                }
                default -> meta.put(k, v);
            }
        }
        return meta;
    }

    /** H2/H3 标题锚点 */
    public static List<Map<String, Object>> extractAnchors(String text) {
        List<Map<String, Object>> anchors = new ArrayList<>();
        Matcher m = Pattern.compile("^#{2,3}\\s+(.+)$", Pattern.MULTILINE).matcher(text);
        while (m.find()) {
            String t = m.group(1).trim().replaceAll("[#*`]", "").trim();
            if (!t.isEmpty()) anchors.add(Map.of("text", t, "anchor", "#" + t));
        }
        return anchors;
    }

    public static String sanitizeId(String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    public static int parseIntSafe(String s, int def) {
        try {
            return (int) Math.floor(Double.parseDouble(s.trim()));
        } catch (RuntimeException e) {
            // JS parseInt 允许前缀数字："3星" → 3
            Matcher m = Pattern.compile("^-?\\d+").matcher(s.trim());
            return m.find() ? Integer.parseInt(m.group()) : def;
        }
    }

    public static Collator zhCollator() {
        return Collator.getInstance(Locale.CHINA);
    }
}

package com.closeloop.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 从模型自由文本中稳健提取 JSON（与 Node ai.js extractJson 对齐）：
 * 去代码围栏 → 按候选起始位置做括号平衡截取（字符串/转义感知）→ 尾随逗号修复重试 → 首尾截断兜底。
 */
public final class JsonExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonExtractor() {}

    public static JsonNode extract(String text) {
        if (text == null || text.isBlank()) throw new FormatReject("AI 返回为空");
        String t = text.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("```\\s*$", "")
                .trim();
        int brace = t.indexOf('{');
        int bracket = t.indexOf('[');
        java.util.List<Integer> positions = new java.util.ArrayList<>();
        if (brace >= 0) positions.add(brace);
        if (bracket >= 0) positions.add(bracket);
        java.util.Collections.sort(positions);
        if (positions.isEmpty()) throw new FormatReject("AI 输出中未找到 JSON");
        for (int s : positions) {
            String raw = sliceBalanced(t, s);
            if (raw != null) {
                JsonNode node = tryParse(raw);
                if (node != null) return node;
            }
        }
        int first = positions.get(0);
        int lastClose = Math.max(t.lastIndexOf('}'), t.lastIndexOf(']'));
        if (lastClose > first) {
            JsonNode node = tryParse(t.substring(first, lastClose + 1));
            if (node != null) return node;
        }
        throw new FormatReject("AI 输出不是合法 JSON");
    }

    /** 从位置 s 截取第一个括号平衡的完整 JSON 片段 */
    private static String sliceBalanced(String t, int s) {
        char open = t.charAt(s);
        if (open != '{' && open != '[') return null;
        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
        boolean inStr = false, esc = false;
        for (int i = s; i < t.length(); i++) {
            char c = t.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == '{' || c == '[') { stack.push(c); continue; }
            if (c == '}' || c == ']') {
                if (stack.isEmpty()) return null;
                char o = stack.pop();
                if ((c == '}' && o != '{') || (c == ']' && o != '[')) return null;
                if (stack.isEmpty()) return t.substring(s, i + 1);
            }
        }
        return null;
    }

    private static JsonNode tryParse(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            try {
                return MAPPER.readTree(raw.replaceAll(",\\s*([}\\]])", "$1"));
            } catch (Exception e2) {
                return null;
            }
        }
    }
}

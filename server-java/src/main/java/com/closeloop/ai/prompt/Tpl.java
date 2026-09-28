package com.closeloop.ai.prompt;

import com.closeloop.ai.AiMessage;
import com.closeloop.state.model.Kp;

import java.util.List;
import java.util.Map;

/** Prompt 文本小工具：§token§ 占位替换（规避 String.format 的 % 转义冲突）+ 双消息组装 */
final class Tpl {

    private Tpl() {}

    static String fill(String template, Map<String, String> vals) {
        String s = template;
        for (Map.Entry<String, String> e : vals.entrySet()) {
            s = s.replace("§" + e.getKey() + "§", e.getValue() == null ? "" : e.getValue());
        }
        return s;
    }

    static List<AiMessage> pair(String system, String user) {
        return List.of(AiMessage.system(system), AiMessage.user(user));
    }

    static String j(String v) { return v == null ? "" : v; }

    static String or(String v, String def) { return v == null || v.isEmpty() ? def : v; }

    static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }
}

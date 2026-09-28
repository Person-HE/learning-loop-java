package com.closeloop.common;

import java.util.LinkedHashMap;
import java.util.Map;

/** 小工具：响应体构造（保持 JS 对象字面量式的键序与契约）、字符串截断 */
public final class Utils {

    private Utils() {}

    /** m("k1", v1, "k2", v2, ...) 构造有序 Map；值为 null 时保留键（与 JSON.stringify 行为一致） */
    public static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    public static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v);
        return s.isEmpty() ? def : s;
    }

    /** 与 JS slice(0,n) 等价的截断（null 安全） */
    public static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** JS 风格 parseInt 失败回退 */
    public static int parseIntOr(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number) return (int) Math.round(((Number) v).doubleValue());
        try {
            double d = Double.parseDouble(String.valueOf(v).trim());
            return (int) Math.round(d);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

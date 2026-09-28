package com.closeloop.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 请求体取值助手：容忍缺失字段，保持与 JS `req.body || {}` 解构一致的默认值语义 */
public final class Req {

    private Req() {}

    public static String str(Map<String, Object> body, String key, String def) {
        Object v = body == null ? null : body.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public static String str(Map<String, Object> body, String key) {
        return str(body, key, "");
    }

    public static boolean bool(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v instanceof Boolean b ? b : v != null && "true".equalsIgnoreCase(String.valueOf(v));
    }

    public static Integer intOrNull(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try {
            return (int) Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Object orNull(Map<String, Object> body, String key) {
        return body == null ? null : body.get(key);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    public static List<String> strList(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (!(v instanceof List<?> l)) return List.of();
        List<String> out = new ArrayList<>(l.size());
        for (Object o : l) if (o != null) out.add(String.valueOf(o));
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v instanceof List ? (List<Map<String, Object>>) v : List.of();
    }
}

package com.closeloop.ai.prompt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提示词注册表（Prompt Engineering as Code）：
 * 每个 scene 绑定版本 / 采样参数 / schema 版本，模板改动是可归因、可回滚的发布单元。
 * 审计日志写入 promptRef，效果对比不凭感觉。
 */
public final class PromptRegistry {

    public record PromptMeta(
            String scene,
            String version,
            double temperature,
            int maxTokens,
            String schemaVersion,
            String status,
            String note
    ) {}

    private static final Map<String, PromptMeta> REGISTRY = new LinkedHashMap<>();

    static {
        // scene, version, temperature, maxTokens, schemaVersion, status, note
        register(new PromptMeta("kb-gen", "v3.1", 0.7, 5000, "quiz@2", "ga", "面试问法铁律 + 手写代码题 T6"));
        register(new PromptMeta("kb-score", "v3.0", 0.3, 4000, "diagnosis@3", "ga", "可追溯分 + 面试官诊断报告"));
        register(new PromptMeta("gap-test", "v3.2", 0.5, 1500, "quiz@2", "ga", "缺口定向检测题"));
        register(new PromptMeta("restate-score", "v3.0", 0.2, 2000, "restate@2", "ga", "合书复述覆盖计数"));
        register(new PromptMeta("kp-points", "v3.0", 0.3, 3000, "points@2", "ga", "考点清单+记忆卡"));
        register(new PromptMeta("weekly", "v3.0", 0.5, 2500, "weekly@2", "ga", "AI 教练周报"));
        register(new PromptMeta("lc-code-score", "v3.0", 0.2, 4000, "code@2", "ga", "手搓代码验收点评分"));
        register(new PromptMeta("lc-ask", "v3.0", 0.6, 1200, "chat@1", "ga", "力扣侧边答疑（自然语言）"));
        register(new PromptMeta("lc-explain", "v3.0", 0.5, 6000, "explain@2", "ga", "零基础精讲生成"));
        register(new PromptMeta("project-questions", "v3.0", 0.7, 3500, "quiz@2", "ga", "项目切面深挖"));
        register(new PromptMeta("project-score", "v3.0", 0.3, 4000, "project@2", "ga", "六维面试官评估"));
        register(new PromptMeta("resume-score", "v3.0", 0.3, 3500, "resume@2", "ga", "简历六维评分"));
        register(new PromptMeta("mock-open", "v3.0", 0.8, 1500, "mock@2", "ga", "模拟面试开场"));
        register(new PromptMeta("mock-turn", "v3.0", 0.8, 1800, "mock@2", "ga", "模拟面试追问"));
        register(new PromptMeta("mock-end", "v3.0", 0.5, 3500, "mock@2", "ga", "模拟面试复盘"));
    }

    private PromptRegistry() {}

    private static void register(PromptMeta meta) {
        REGISTRY.put(meta.scene(), meta);
    }

    public static PromptMeta require(String scene) {
        PromptMeta m = REGISTRY.get(scene);
        if (m == null) throw new IllegalArgumentException("未注册的 prompt scene: " + scene);
        return m;
    }

    public static Map<String, PromptMeta> all() {
        return Map.copyOf(REGISTRY);
    }

    /** 审计用引用：scene@version */
    public static String ref(String scene) {
        PromptMeta m = require(scene);
        return m.scene() + "@" + m.version();
    }
}

package com.closeloop.learning;

import com.closeloop.state.model.Kp;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 出题优先级策略（用户指定的两条规则，集中于此便于调整）：
 * 1. 低优先级域（网络编程/云原生/计组/分布式）排最后；
 * 2. Java 后端优先出「简历项目中实际用到的技术」。
 */
public final class Priority {

    private Priority() {}

    public static final List<String> LOW_PRIORITY_DOMAINS = List.of("network", "cloud-native", "comput-arch", "distributed");

    public static final List<String> PROJECT_TECH_DOMAINS = List.of("redis", "mysql", "mq", "spring", "concurrent", "jvm", "java");

    public static final List<String> PROJECT_TECH_KEYWORDS = List.of(
            "docker", "websocket", "aop", "jwt", "security", "mybatis", "caffeine", "rabbit", "langchain",
            "agent", "rag", "onnx", "embedding", "sse", "流式", "熔断", "降级", "重试", "线程池", "async",
            "completablefuture", "concurrenthashmap", "threadlocal", "bcrypt", "aes", "脱敏", "切面",
            "事件驱动", "topic", "交换机", "策略", "分布式锁", "缓存", "lua", "限流", "死信", "redis", "索引", "事务"
    );

    public static boolean isProjectTech(Kp kp) {
        if (kp == null) return false;
        if (PROJECT_TECH_DOMAINS.contains(kp.domain)) return true;
        String title = (kp.title == null ? "" : kp.title).toLowerCase(Locale.ROOT);
        return PROJECT_TECH_KEYWORDS.stream().anyMatch(k -> title.contains(k.toLowerCase(Locale.ROOT)));
    }

    /** 缺口标签权重（薄弱补强排序用） */
    public static final Set<String> GAP_LABEL_WEIGHT_KEYS = Set.of("概念混淆", "因果链断裂", "边界缺失", "术语不准", "表达卡顿", "空白");

    public static int gapLabelWeight(String label) {
        return switch (label == null ? "" : label) {
            case "空白" -> 4;
            case "概念混淆", "因果链断裂" -> 3;
            case "边界缺失", "术语不准" -> 2;
            case "表达卡顿" -> 1;
            default -> 0;
        };
    }
}

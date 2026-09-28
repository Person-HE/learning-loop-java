package com.closeloop.ai.prompt;

import com.closeloop.project.ResumeData;
import com.closeloop.state.model.Kp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 简历技术栈挂钩：知识点命中简历 5 大亮点模块时，为出题 Prompt 注入「考点范围指引」。
 * 只对齐技术栈，不泄露项目名/个人经历（问法保持通用八股）。
 */
public final class ResumeContext {

    private ResumeContext() {}

    private record Rule(List<Integer> mods, List<String> keys) {}

    private static final List<Rule> PROJ_MATCH_RULES = List.of(
            new Rule(List.of(1), List.of("redis", "缓存", "caffeine", "穿透", "击穿", "雪崩", "分布式锁", "setnx", "lua", "限流", "local", "内存")),
            new Rule(List.of(2), List.of("mq", "rabbit", "消息", "死信", "交换机", "队列", "async", "异步", "事件驱动", "topic", "confirm")),
            new Rule(List.of(3), List.of("langchain", "agent", "rag", "embedding", "onnx", "sse", "流式", "熔断", "降级", "重试", "llm", "ai", "function calling", "函数调用", "智能体")),
            new Rule(List.of(4), List.of("mysql", "索引", "事务", "sql", "n+1", "join", "分页", "mvcc", "锁", "b+", "b树", "explain", "redo", "undo", "binlog")),
            new Rule(List.of(5), List.of("spring", "aop", "切面", "security", "jwt", "bcrypt", "aes", "脱敏", "threadlocal", "注解", "限流", "日志", "mvc", "mybatis", "ioc", "bean")));

    static List<Integer> matchModules(Kp kp) {
        String hay = (Tpl.j(kp.title) + " " + Tpl.j(kp.domain) + " " + Tpl.j(kp.id)).toLowerCase(Locale.ROOT);
        Set<Integer> hit = new LinkedHashSet<>();
        for (Rule rule : PROJ_MATCH_RULES) {
            if (rule.keys().stream().anyMatch(hay::contains)) hit.addAll(rule.mods());
        }
        List<Integer> out = new ArrayList<>(hit);
        out.sort(Comparator.naturalOrder());
        return out;
    }

    public static String build(Kp kp) {
        List<Integer> mods = matchModules(kp);
        if (mods.isEmpty()) return "";
        StringBuilder lines = new StringBuilder();
        for (int no : mods) {
            ResumeData.Module m = ResumeData.MODULES.stream().filter(x -> x.no() == no).findFirst().orElse(null);
            if (m == null) continue;
            String text = m.text().length() > 150 ? m.text().substring(0, 150) + "…" : m.text();
            if (lines.length() > 0) lines.append('\n');
            lines.append("【模块").append(no).append("】").append(m.title())
                    .append("｜真实技术栈：").append(String.join("、", m.techs()))
                    .append("｜实现要点：").append(text);
        }
        return "\n【候选人简历中的相关真实技术栈（考点范围指引，不是题干素材）】\n"
                + "该知识点命中候选人简历项目「" + ResumeData.P_NAME + "」中实际使用并写进简历的技术栈。你的任务：把这道八股考到「他简历里写了的那个技术」上——考点范围对齐简历真实技术栈及其真实实现所涉及的技术点，但**问法必须是通用八股**（原理/机制/对比/边界/线上通用场景），**严禁**使用「你在XX项目里怎么做的」「你的XX里为什么选X」这类项目深挖句式——项目深挖题由专门的「项目面试」模块负责，这里只出标准八股。\n"
                + "命中技术栈：\n" + lines + "\n"
                + "出题要求：5 道题优先覆盖上述技术栈相关的八股考点（例如命中 Caffeine 就考 Caffeine 淘汰策略/与 Redis 的定位差异，命中 SETNX 就考 SETNX 原理与 Redisson 对比，命中死信队列就考死信队列机制与场景）；题目用通用面试官问法（八股直问/场景/辨析），允许用通用线上场景（如「缓存穿透怎么防」）但不得点名简历项目名与个人经历。";
    }
}

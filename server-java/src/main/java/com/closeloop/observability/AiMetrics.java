package com.closeloop.observability;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 轻量 AI 运行时指标（可观测性）：首过率、修复轮数、结果分类、时延。
 * 不绑 Micrometer 也能在 /api/ops/metrics 一眼看清质量与成本——单机自用规模下这是正确选择。
 */
@Component
public class AiMetrics {

    public static final class SceneStats {
        public final LongAdder calls = new LongAdder();
        public final LongAdder ok = new LongAdder();
        public final LongAdder formatReject = new LongAdder();
        public final LongAdder firstPass = new LongAdder();
        public final LongAdder repairRounds = new LongAdder();
        public final LongAdder tokensIn = new LongAdder();
        public final LongAdder tokensOut = new LongAdder();
        public final LongAdder latencySumMs = new LongAdder();
        public final Map<String, LongAdder> outcomes = new ConcurrentHashMap<>();
    }

    private final ConcurrentHashMap<String, SceneStats> byScene = new ConcurrentHashMap<>();
    private final AtomicLong quotaBlocks = new AtomicLong();
    private final AtomicLong breakerOpens = new AtomicLong();
    private final AtomicLong breakerBlocks = new AtomicLong();

    public SceneStats scene(String scene) {
        return byScene.computeIfAbsent(scene == null ? "unknown" : scene, s -> new SceneStats());
    }

    public void recordAttempt(String scene, String outcome, String code, int repairRounds,
                              int tokensIn, int tokensOut, long latencyMs, boolean firstPass) {
        SceneStats s = scene(scene);
        s.calls.increment();
        s.outcomes.computeIfAbsent(outcome == null ? "?" : outcome, k -> new LongAdder()).increment();
        s.repairRounds.add(Math.max(0, repairRounds));
        s.tokensIn.add(Math.max(0, tokensIn));
        s.tokensOut.add(Math.max(0, tokensOut));
        s.latencySumMs.add(Math.max(0, latencyMs));
        if ("OK".equals(outcome)) {
            s.ok.increment();
            if (firstPass) s.firstPass.increment();
        }
        if ("FORMAT".equals(outcome) || "FORMAT".equals(code)) {
            s.formatReject.increment();
        }
    }

    public void incQuotaBlock() { quotaBlocks.incrementAndGet(); }
    public void incBreakerOpen() { breakerOpens.incrementAndGet(); }
    public void incBreakerBlock() { breakerBlocks.incrementAndGet(); }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("quotaBlocks", quotaBlocks.get());
        out.put("breakerOpens", breakerOpens.get());
        out.put("breakerBlocks", breakerBlocks.get());
        Map<String, Object> scenes = new LinkedHashMap<>();
        byScene.forEach((name, s) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            long calls = s.calls.sum();
            long ok = s.ok.sum();
            long fp = s.firstPass.sum();
            m.put("calls", calls);
            m.put("ok", ok);
            m.put("firstPass", fp);
            m.put("firstPassRate", calls == 0 ? 1.0 : Math.round(fp * 10000.0 / calls) / 10000.0);
            m.put("formatReject", s.formatReject.sum());
            m.put("repairRounds", s.repairRounds.sum());
            m.put("avgRepairRounds", ok == 0 ? 0.0 : Math.round(s.repairRounds.sum() * 100.0 / Math.max(1, ok)) / 100.0);
            m.put("tokensIn", s.tokensIn.sum());
            m.put("tokensOut", s.tokensOut.sum());
            m.put("avgLatencyMs", calls == 0 ? 0 : s.latencySumMs.sum() / calls);
            Map<String, Long> oc = new LinkedHashMap<>();
            s.outcomes.forEach((k, v) -> oc.put(k, v.sum()));
            m.put("outcomes", oc);
            scenes.put(name, m);
        });
        out.put("scenes", scenes);
        return out;
    }
}

package com.closeloop.ai.govern;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 用户级 AI 日调用配额（可控性）：超限直接 429 BUDGET，不打上游。
 * 配额按「日」重置；可用 tokens 粗估记录消耗。
 */
public final class QuotaGuard {

    private final int maxCallsPerDay;
    private final int maxTokensPerDay;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger tokens = new AtomicInteger();
    private final AtomicReference<LocalDate> day = new AtomicReference<>(LocalDate.now());

    public QuotaGuard(int maxCallsPerDay, int maxTokensPerDay) {
        this.maxCallsPerDay = Math.max(1, maxCallsPerDay);
        this.maxTokensPerDay = Math.max(1000, maxTokensPerDay);
    }

    public static QuotaGuard defaults() {
        // 单人自用：给足余量但仍可治理与演示
        return new QuotaGuard(400, 2_000_000);
    }

    public void beforeCall(int estimatedInTokens) {
        rollDay();
        if (calls.get() >= maxCallsPerDay) {
            throw blocked("今日 AI 调用次数已达上限（" + maxCallsPerDay + "），明日自动恢复或调高 app.ai.quota.*");
        }
        if (tokens.get() + estimatedInTokens > maxTokensPerDay) {
            throw blocked("今日 AI Token 预算已达上限（" + maxTokensPerDay + "）");
        }
        calls.incrementAndGet();
        tokens.addAndGet(Math.max(0, estimatedInTokens));
    }

    public void recordActual(int inTokens, int outTokens) {
        rollDay();
        // 入参已在 beforeCall 预估计入，这里补记输出侧
        tokens.addAndGet(Math.max(0, outTokens));
    }

    public MapView snapshot() {
        rollDay();
        return new MapView(calls.get(), tokens.get(), maxCallsPerDay, maxTokensPerDay, day.get().toString());
    }

    public void reset() {
        calls.set(0);
        tokens.set(0);
        day.set(LocalDate.now());
    }

    private void rollDay() {
        LocalDate today = LocalDate.now();
        if (!day.get().equals(today)) {
            day.set(today);
            calls.set(0);
            tokens.set(0);
        }
    }

    private static com.closeloop.common.ApiException blocked(String msg) {
        return new com.closeloop.common.ApiException(msg, "BUDGET",
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
    }

    public record MapView(int calls, int tokens, int maxCalls, int maxTokens, String day) {}
}

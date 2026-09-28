package com.closeloop.ai.govern;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 上游熔断器：连续 AUTH/UPSTREAM 失败达阈值后 OPEN 一段时间，避免雪崩打上游。
 * CLOSED → OPEN（连续 N 次故障）→ HALF_OPEN（冷却后放行探测）→ CLOSED（成功）/ OPEN（再失败）。
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openMillis;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong openedAt = new AtomicLong();
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    public CircuitBreaker(int failureThreshold, long openMillis) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openMillis = Math.max(1000, openMillis);
    }

    public static CircuitBreaker defaults() {
        return new CircuitBreaker(5, 60_000);
    }

    /** 调用前检查；OPEN 且未到冷却直接抛业务异常语义（由调用方转 ApiException） */
    public void beforeCall() {
        State s = state.get();
        if (s == State.OPEN) {
            if (System.currentTimeMillis() - openedAt.get() >= openMillis) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                return;
            }
            throw new com.closeloop.common.ApiException(
                    "AI 服务熔断中（连续失败保护），请 " + remainingCooldownMs() / 1000 + " 秒后再试",
                    "BREAKER",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public void onSuccess() {
        failures.set(0);
        state.set(State.CLOSED);
    }

    /** 仅统计上游/鉴权类失败；RATE 与格式问题不熔断 */
    public void onUpstreamFailure(String code) {
        if (code == null) return;
        if (!("AUTH".equals(code) || "UPSTREAM".equals(code) || "NET".equals(code) || "EMPTY".equals(code))) {
            return;
        }
        int n = failures.incrementAndGet();
        if (n >= failureThreshold || "AUTH".equals(code)) {
            openedAt.set(System.currentTimeMillis());
            state.set(State.OPEN);
        }
    }

    public State currentState() {
        if (state.get() == State.OPEN && System.currentTimeMillis() - openedAt.get() >= openMillis) {
            state.compareAndSet(State.OPEN, State.HALF_OPEN);
        }
        return state.get();
    }

    public long remainingCooldownMs() {
        if (state.get() != State.OPEN) return 0;
        long left = openMillis - (System.currentTimeMillis() - openedAt.get());
        return Math.max(0, left);
    }

    public void reset() {
        failures.set(0);
        state.set(State.CLOSED);
    }
}

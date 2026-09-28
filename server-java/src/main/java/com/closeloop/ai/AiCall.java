package com.closeloop.ai;

import java.util.List;

/**
 * 一次 AI 调用的完整参数（值对象）：messages + 采样配置 + scene（Prompt Registry 归因）。
 * timeoutSeconds：0 = 用全局 app.ai.timeout-seconds；慢场景（如 weekly 上游长尾）可收紧倒逼重试。
 */
public record AiCall(
        List<AiMessage> messages,
        double temperature,
        int maxTokens,
        String scene,
        int timeoutSeconds
) {
    public AiCall(List<AiMessage> messages, double temperature, int maxTokens) {
        this(messages, temperature, maxTokens, "unknown", 0);
    }

    public AiCall(List<AiMessage> messages, double temperature, int maxTokens, String scene) {
        this(messages, temperature, maxTokens, scene, 0);
    }

    public AiCall withMessages(List<AiMessage> newMessages) {
        return new AiCall(newMessages, temperature, maxTokens, scene, timeoutSeconds);
    }

    public AiCall withMaxTokens(int tokens) {
        return new AiCall(messages, temperature, tokens, scene, timeoutSeconds);
    }

    public AiCall withScene(String s) {
        return new AiCall(messages, temperature, maxTokens, s == null || s.isBlank() ? "unknown" : s, timeoutSeconds);
    }

    public AiCall withTimeoutSeconds(int secs) {
        return new AiCall(messages, temperature, maxTokens, scene, secs);
    }
}

package com.closeloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 服务配置（app.ai.*）。
 * 密钥优先读环境变量 AI_API_KEY（见 application.yml 占位），运行时不可被设置页覆盖。
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String baseUrl,
        String apiKey,
        String model,
        @DefaultValue("180") int timeoutSeconds,
        @DefaultValue("3") int maxRetries,
        @DefaultValue("60") int weeklyTimeoutSeconds,
        @DefaultValue Quota quota,
        @DefaultValue Breaker breaker
) {
    public record Quota(
            @DefaultValue("400") int maxCallsPerDay,
            @DefaultValue("2000000") int maxTokensPerDay
    ) {}

    public record Breaker(
            @DefaultValue("5") int failureThreshold,
            @DefaultValue("60000") long openMillis
    ) {}
}

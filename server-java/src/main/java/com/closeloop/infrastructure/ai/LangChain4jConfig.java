package com.closeloop.infrastructure.ai;

import com.closeloop.config.AiProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 装配。
 * 问题：自写 JDK HttpClient 调 /chat/completions 与生态割裂，重试/结构化各写一套。
 * 决策：Chat 走 LangChain4j OpenAiChatModel（Agnes 为 OpenAI 兼容）；
 *       复杂 JSON 治理仍可挂 AiClient（结构化自修复），两者共用 baseUrl/key/model 配置。
 */
@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(AiProperties ai) {
        return OpenAiChatModel.builder()
                .baseUrl(ai.baseUrl())
                .apiKey(ai.apiKey())
                .modelName(ai.model())
                .timeout(Duration.ofSeconds(ai.timeoutSeconds()))
                .maxRetries(ai.maxRetries())
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}

package com.closeloop.ai;

import com.closeloop.ai.context.ContextPlan;
import com.closeloop.ai.context.TokenEstimator;
import com.closeloop.ai.govern.CircuitBreaker;
import com.closeloop.ai.govern.QuotaGuard;
import com.closeloop.ai.prompt.PromptRegistry;
import com.closeloop.common.ApiException;
import com.closeloop.config.AiProperties;
import com.closeloop.observability.AiAuditLog;
import com.closeloop.observability.AiMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AI 网关（驾驭工程核心）——唯一出站出口。装饰器顺序显式定义：
 *
 *   业务 service
 *     └─ Audit（成功失败都留痕）
 *         └─ Quota（日预算，超限 429 BUDGET，不打上游）
 *             └─ CircuitBreaker（AUTH/UPSTREAM 连续失败熔断）
 *                 └─ Retry（网络/5xx/429/空返回指数退避；AUTH 永不重试）
 *                     └─ HTTP（JDK HttpClient /chat/completions）
 *
 * 结构化输出保障：chatJson = 调用 → JsonExtractor → 领域 validator
 * → 不合格带 assistant 回显 + 固定中文纠错，最多 3 轮自修复，maxTokens×1.5（上限 8000）。
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final AiProperties ai;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CircuitBreaker breaker;
    private final QuotaGuard quota;
    private final AiAuditLog auditLog;
    private final AiMetrics metrics;

    public AiClient(AiProperties props, AiAuditLog auditLog, AiMetrics metrics) {
        this.ai = props;
        this.auditLog = auditLog;
        this.metrics = metrics;
        this.breaker = new CircuitBreaker(
                props.breaker() == null ? 5 : props.breaker().failureThreshold(),
                props.breaker() == null ? 60_000 : props.breaker().openMillis());
        this.quota = new QuotaGuard(
                props.quota() == null ? 400 : props.quota().maxCallsPerDay(),
                props.quota() == null ? 2_000_000 : props.quota().maxTokensPerDay());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean hasKey() {
        return ai.apiKey() != null && !ai.apiKey().isBlank();
    }

    public AiProperties props() { return ai; }

    public CircuitBreaker breaker() { return breaker; }

    public QuotaGuard quota() { return quota; }

    /** 文本对话：治理链 + 重试，返回 assistant 内容 */
    public String chat(AiCall call) {
        if (!hasKey()) throw ApiException.auth("未配置 AI API Key（环境变量 AI_API_KEY 或 app.ai.api-key），请配置后重启");
        String scene = call.scene() == null ? "unknown" : call.scene();
        String promptRef = safeRef(scene);
        int estIn = estimateIn(call);
        long t0 = System.currentTimeMillis();

        try {
            quota.beforeCall(estIn);
        } catch (ApiException e) {
            metrics.incQuotaBlock();
            audit(call, promptRef, 0, 0, "BUDGET", e.getCode(), estIn, 0, System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }

        try {
            breaker.beforeCall();
        } catch (ApiException e) {
            metrics.incBreakerBlock();
            audit(call, promptRef, 0, 0, "BREAKER", e.getCode(), estIn, 0, System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }

        int retries = Math.max(0, ai.maxRetries());
        RuntimeException last = null;
        int attemptUsed = 0;
        for (int attempt = 0; attempt <= retries; attempt++) {
            attemptUsed = attempt + 1;
            try {
                String content = doCall(call);
                breaker.onSuccess();
                int estOut = TokenEstimator.estimate(content);
                quota.recordActual(0, estOut);
                metrics.recordAttempt(scene, "OK", null, 0, estIn, estOut, System.currentTimeMillis() - t0, true);
                audit(call, promptRef, attemptUsed, 0, "OK", null, estIn, estOut, System.currentTimeMillis() - t0, null);
                return content;
            } catch (ApiException e) {
                last = e;
                breaker.onUpstreamFailure(e.getCode());
                if ("AUTH".equals(e.getCode())) {
                    metrics.recordAttempt(scene, "AUTH", e.getCode(), 0, estIn, 0, System.currentTimeMillis() - t0, false);
                    audit(call, promptRef, attemptUsed, 0, "AUTH", e.getCode(), estIn, 0, System.currentTimeMillis() - t0, e.getMessage());
                    throw e;
                }
                if (attempt >= retries) break;
                sleepBackoff(attempt);
            } catch (Exception e) {
                last = new ApiException("网络错误：" + e.getMessage(), "NET", null);
                breaker.onUpstreamFailure("NET");
                if (attempt >= retries) break;
                sleepBackoff(attempt);
            }
        }
        String code = last instanceof ApiException ae && ae.getCode() != null ? ae.getCode() : "NET";
        metrics.recordAttempt(scene, code, code, 0, estIn, 0, System.currentTimeMillis() - t0, false);
        audit(call, promptRef, attemptUsed, 0, code, code, estIn, 0, System.currentTimeMillis() - t0,
                last == null ? "unknown" : last.getMessage());
        throw last == null ? ApiException.internal("AI 调用失败") : last;
    }

    /**
     * 结构化调用：JSON 提取 + 业务校验（validator 抛 FormatReject 触发纠错重试）。
     * 纠错方式：上次失败输出作 assistant 上下文 + 追加纠错指令，最多 3 轮。
     */
    public JsonNode chatJson(AiCall call, Consumer<JsonNode> validator) {
        String[] hints = {
                "你上次的输出不是合法 JSON。请只输出一个合法 JSON（不要代码块标记、不要解释文字），严格按我要求的字段输出。",
                "你上次的输出仍然无法解析。现在必须：1) 任何解释文字都不要；2) 直接以 [ 或 { 开头；3) 若内容过长截断了，请只保留前 3 项；4) 字符串内不要出现未转义换行。"
        };
        String scene = call.scene() == null ? "unknown" : call.scene();
        String promptRef = safeRef(scene);
        long t0 = System.currentTimeMillis();
        int estIn = estimateIn(call);
        AiCall opts = call;
        String lastContent = "";
        int repair = 0;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String content = chat(opts);
                lastContent = content;
                JsonNode data = JsonExtractor.extract(content);
                if (validator != null) validator.accept(data);
                metrics.recordAttempt(scene, "OK", null, repair, estIn, TokenEstimator.estimate(content),
                        System.currentTimeMillis() - t0, repair == 0);
                return data;
            } catch (FormatReject e) {
                repair++;
                log.warn("[ai] scene={} 第 {} 轮输出格式不合格：{}", scene, attempt, e.getMessage());
                metrics.recordAttempt(scene, "FORMAT", "FORMAT", repair, estIn, 0, System.currentTimeMillis() - t0, false);
                audit(opts, promptRef, attempt, repair, "FORMAT", "FORMAT", estIn, 0,
                        System.currentTimeMillis() - t0, e.getMessage());
                if (attempt >= 3) {
                    throw ApiException.withCode("AI 输出无法解析为 JSON，请重试或重新出题", "FORMAT");
                }
                List<AiMessage> msgs = new ArrayList<>(opts.messages());
                msgs.add(AiMessage.assistant(lastContent));
                msgs.add(AiMessage.user(hints[attempt - 1]));
                opts = opts.withMessages(msgs)
                        .withMaxTokens(Math.min(8000, (int) Math.round(opts.maxTokens() * 1.5)));
            }
        }
        throw ApiException.withCode("AI 输出无法解析，请重试", "FORMAT");
    }

    public JsonNode chatJson(AiCall call) {
        return chatJson(call, null);
    }

    /** 按 Prompt Registry 规格包装调用（scene 归因 + 默认采样参数） */
    public JsonNode chatJsonScene(String scene, List<AiMessage> messages, Consumer<JsonNode> validator) {
        PromptRegistry.PromptMeta meta = PromptRegistry.require(scene);
        AiCall call = new AiCall(messages, meta.temperature(), meta.maxTokens(), scene);
        return chatJson(call, validator);
    }

    public String chatScene(String scene, List<AiMessage> messages) {
        PromptRegistry.PromptMeta meta = PromptRegistry.require(scene);
        return chat(new AiCall(messages, meta.temperature(), meta.maxTokens(), scene));
    }

    public ContextPlan.Assembled lastAssembled; // 供调试；生产走审计 digest

    private static String safeRef(String scene) {
        try {
            return PromptRegistry.ref(scene);
        } catch (Exception e) {
            return scene + "@unregistered";
        }
    }

    private static int estimateIn(AiCall call) {
        int n = 0;
        for (AiMessage m : call.messages()) n += TokenEstimator.estimate(m.content());
        return n;
    }

    private void audit(AiCall call, String promptRef, int attempt, int repair, String outcome, String code,
                       int tokensIn, int tokensOut, long latencyMs, String error) {
        String firstUser = "";
        for (AiMessage m : call.messages()) {
            if ("user".equals(m.role())) {
                firstUser = m.content();
                break;
            }
        }
        auditLog.record(new AiAuditLog.AiAuditEvent(
                MDC.get("requestId"),
                call.scene(),
                promptRef,
                ai.model(),
                attempt,
                repair,
                outcome,
                code,
                tokensIn,
                tokensOut,
                latencyMs,
                "tokensIn~" + tokensIn,
                firstUser,
                null,
                error
        ));
    }

    // ---------- HTTP 单次调用 ----------

    private String doCall(AiCall call) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", ai.model());
        body.put("temperature", call.temperature());
        body.put("max_tokens", call.maxTokens());
        var msgs = body.putArray("messages");
        for (AiMessage m : call.messages()) {
            msgs.addObject().put("role", m.role()).put("content", m.content());
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ai.baseUrl().replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(call.timeoutSeconds() > 0 ? call.timeoutSeconds() : ai.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + ai.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = res.statusCode();
        if (status == 401 || status == 403) {
            throw ApiException.auth("API Key 无效或无权限（HTTP " + status + "），请检查 AI_API_KEY / app.ai.api-key");
        }
        if (status == 429) {
            throw ApiException.rate("AI 请求过于频繁（HTTP 429），自动等待后重试");
        }
        if (status >= 400) {
            String detail = "";
            try {
                JsonNode e = mapper.readTree(res.body());
                JsonNode err = e.path("error").path("message");
                detail = err.isMissingNode() ? res.body() : err.asText();
                if (detail == null || detail.isEmpty()) detail = res.body();
            } catch (Exception ignored) {
                detail = res.body() == null ? "" : res.body();
            }
            if (detail.length() > 300) detail = detail.substring(0, 300);
            throw ApiException.withCode("AI 请求失败（HTTP " + status + "）：" + detail, "UPSTREAM");
        }
        JsonNode j = mapper.readTree(res.body());
        String content = j.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) throw ApiException.withCode("AI 返回内容为空，自动重试", "EMPTY");
        return content;
    }

    private static void sleepBackoff(int attempt) {
        try {
            long delay = Math.round(500 * Math.pow(2, attempt)) + (long) (Math.random() * 500);
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.closeloop.web;

import com.closeloop.ai.AiClient;
import com.closeloop.ai.prompt.PromptRegistry;
import com.closeloop.observability.AiAuditLog;
import com.closeloop.observability.AiMetrics;
import com.closeloop.service.StateViewService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运维与治理端点（可观测性 / 可控性）：
 *  - /api/ops/health   运行时就绪（密钥、熔断、配额、状态库）
 *  - /api/ops/metrics  AI 质量与成本指标
 *  - /api/ops/prompts  Prompt Registry 版本清单
 *  - /api/ops/breaker  熔断状态查看/复位
 *  - /api/ops/quota    配额查看/复位
 */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final AiClient ai;
    private final AiMetrics metrics;
    private final AiAuditLog auditLog;
    private final StateViewService view;

    public OpsController(AiClient ai, AiMetrics metrics, AiAuditLog auditLog, StateViewService view) {
        this.ai = ai;
        this.metrics = metrics;
        this.auditLog = auditLog;
        this.view = view;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("aiKeyConfigured", ai.hasKey());
        m.put("aiModel", ai.props().model());
        m.put("aiBaseUrl", ai.props().baseUrl());
        m.put("breaker", Map.of(
                "state", ai.breaker().currentState().name(),
                "cooldownMs", ai.breaker().remainingCooldownMs()
        ));
        m.put("quota", ai.quota().snapshot());
        m.put("stateKps", view.publicState().get("kps") instanceof Map<?, ?> k ? k.size() : 0);
        m.put("auditDir", String.valueOf(auditLog.dir()));
        m.put("ts", System.currentTimeMillis());
        return m;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metrics.snapshot();
    }

    @GetMapping("/prompts")
    public Map<String, Object> prompts() {
        Map<String, Object> m = new LinkedHashMap<>();
        PromptRegistry.all().forEach((k, v) -> m.put(k, Map.of(
                "scene", v.scene(),
                "version", v.version(),
                "temperature", v.temperature(),
                "maxTokens", v.maxTokens(),
                "schemaVersion", v.schemaVersion(),
                "status", v.status(),
                "note", v.note()
        )));
        return Map.of("prompts", m);
    }

    @PostMapping("/breaker/reset")
    public Map<String, Object> breakerReset() {
        ai.breaker().reset();
        return Map.of("ok", true, "state", ai.breaker().currentState().name());
    }

    @PostMapping("/quota/reset")
    public Map<String, Object> quotaReset() {
        ai.quota().reset();
        return Map.of("ok", true, "quota", ai.quota().snapshot());
    }
}

package com.closeloop.observability;

import com.closeloop.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * AI 调用全量审计（JSONL，按日滚动）。
 * 字段对齐企业治理：trace/scene/promptRef/model/attempt/outcome/tokens/latency/contextDigest/摘要脱敏。
 * 审计即数据资产：成本归因、漂移监控、评测集挖掘都从这张表出。
 */
@Component
public class AiAuditLog {

    private static final Logger log = LoggerFactory.getLogger(AiAuditLog.class);
    private static final Pattern SENSITIVE = Pattern.compile(
            "(sk-[A-Za-z0-9]{8,})|(1[3-9]\\d{9})|([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path dir;

    public AiAuditLog(AppProperties props) {
        this.dir = props.dataPath().resolve("audit");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            log.warn("[audit] 创建审计目录失败：{}", e.getMessage());
        }
    }

    public void record(AiAuditEvent e) {
        try {
            ObjectNode n = mapper.createObjectNode();
            n.put("ts", System.currentTimeMillis());
            n.put("traceId", MDC.get("requestId") == null ? e.traceId() : MDC.get("requestId"));
            n.put("scene", e.scene());
            n.put("promptRef", e.promptRef());
            n.put("model", e.model());
            n.put("attempt", e.attempt());
            n.put("repairRounds", e.repairRounds());
            n.put("outcome", e.outcome());
            n.put("code", e.code());
            n.put("tokensIn", e.tokensIn());
            n.put("tokensOut", e.tokensOut());
            n.put("latencyMs", e.latencyMs());
            n.put("contextDigest", e.contextDigest());
            n.put("requestExcerpt", redact(excerpt(e.requestExcerpt())));
            n.put("responseExcerpt", redact(excerpt(e.responseExcerpt())));
            if (e.error() != null) n.put("error", redact(excerpt(e.error())));
            Path file = dir.resolve("ai-audit-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".jsonl");
            Files.writeString(file, mapper.writeValueAsString(n) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            log.warn("[audit] 写入失败（不影响主流程）：{}", ex.getMessage());
        }
    }

    public Path dir() {
        return dir;
    }

    private static String excerpt(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= 512 ? t : t.substring(0, 512);
    }

    private static String redact(String s) {
        if (s == null || s.isEmpty()) return "";
        return SENSITIVE.matcher(s).replaceAll(mr -> {
            String g = mr.group();
            if (g.startsWith("sk-")) return "sk-***";
            if (g.contains("@")) return "***@***";
            return "***";
        });
    }

    public record AiAuditEvent(
            String traceId,
            String scene,
            String promptRef,
            String model,
            int attempt,
            int repairRounds,
            String outcome,
            String code,
            int tokensIn,
            int tokensOut,
            long latencyMs,
            String contextDigest,
            String requestExcerpt,
            String responseExcerpt,
            String error
    ) {}
}

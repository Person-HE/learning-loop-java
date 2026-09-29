package com.closeloop.infrastructure.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地嵌入：Ollama bge-m3，1024 维，L2 归一化。
 *
 * 换模原因（2026-09-29 实测）：
 * - nomic-embed-text 是英文为主模型，中文短查询向量塌陷：
 *   「Redis缓存穿透」vs「MySQL索引优化」余弦 0.9989，检索完全无法区分主题；
 *   同等英文查询余弦仅 0.48。
 * - bge-m3 为中文/多语优化模型（BGE M3-Embedding），1024 维，8192 上下文。
 * - Know 项目同步切换到 bge-m3（OllamaEmbeddingProvider）。
 *
 * 维度变更 768→1024：不能复用旧 collection，配置项 app.vector.collection
 * 默认 learning_chunks_bge，全量 reindex 重建。
 */
@Component
public class OllamaBgeEmbeddingModel implements EmbeddingModel {

    public static final String MODEL_ID = "bge-m3";
    public static final int DIM = 1024;
    public static final String SOURCE = "ollama+bge-m3";

    private static final Logger log = LoggerFactory.getLogger(OllamaBgeEmbeddingModel.class);
    private final EmbeddingModel delegate;

    public OllamaBgeEmbeddingModel() {
        this.delegate = OllamaEmbeddingModel.builder()
                .baseUrl("http://127.0.0.1:11434")
                .modelName(MODEL_ID)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    public OllamaBgeEmbeddingModel(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    /**
     * 真实问题（2026-09-23 全量索引 200/201）：Ollama 报 the input length exceeds the context length。
     * 根因：切块偶发超长。取舍：嵌入输入截断 1800 字符；展示/检索文本仍用完整块。
     */
    public static String clampForEmbed(String text) {
        if (text == null) return "";
        return text.length() <= 1800 ? text : text.substring(0, 1800);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        long t0 = System.nanoTime();
        List<Embedding> out = new ArrayList<>();
        final int BATCH = 4;
        for (int i = 0; i < segments.size(); i += BATCH) {
            List<TextSegment> batch = segments.subList(i, Math.min(segments.size(), i + BATCH));
            List<TextSegment> clamped = new ArrayList<>(batch.size());
            for (TextSegment s : batch) {
                clamped.add(TextSegment.from(clampForEmbed(s.text()), s.metadata()));
            }
            List<Embedding> got;
            try {
                got = delegate.embedAll(clamped).content();
            } catch (RuntimeException ex) {
                for (TextSegment s : clamped) out.add(embedWithSymbolStrip(s.text(), ex));
                continue;
            }
            for (int j = 0; j < got.size(); j++) {
                float[] v = got.get(j).vector();
                if (hasNonFinite(v)) {
                    out.add(embedWithSymbolStrip(clamped.get(j).text(), new IllegalStateException("NaN in embedding vector")));
                } else {
                    out.add(Embedding.from(l2Normalize(v)));
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.debug("embed n={} ms={} model={}", segments.size(), ms, MODEL_ID);
        return Response.from(out);
    }

    /**
     * 真实问题（2026-09-29 全量索引 200/201，mq/04-Kafka架构 尾块 40 字确定性复现）：
     * 含「→ + 全角）」的短片段让 Ollama bge-m3 返回 NaN，整条 REST 响应 500
     * （failed to encode response: json: unsupported value: NaN），删掉箭头或右括号即正常。
     * 取舍：仅失败时降级——去掉非 ASCII 非汉字符号后重嵌，正常文本的向量不受影响。
     */
    private Embedding embedWithSymbolStrip(String text, RuntimeException cause) {
        String cleaned = text.replaceAll("[^\\x00-\\x7E\\p{IsHan}]", " ").replaceAll("\\s{2,}", " ").trim();
        log.warn("嵌入含 NaN，去符号重嵌 origin={} cleaned={} cause={}",
                text.length(), cleaned.length(), cause.getMessage());
        return Embedding.from(l2Normalize(delegate.embed(TextSegment.from(clampForEmbed(cleaned))).content().vector()));
    }

    private static boolean hasNonFinite(float[] v) {
        for (float x : v) if (!Float.isFinite(x)) return true;
        return false;
    }

    /**
     * 统一在入口做 L2 归一化，保证 Qdrant Cosine 内积口径稳定
     *（Ollama 不同嵌入模型是否自带归一化不保证：nomic 实测 norm≈22.6）。
     */
    public static float[] l2Normalize(float[] vec) {
        double sum = 0;
        for (float v : vec) sum += v * v;
        if (!Double.isFinite(sum) || sum <= 1e-6) return vec;
        float inv = (float) (1.0 / Math.sqrt(sum));
        float[] out = new float[vec.length];
        for (int i = 0; i < vec.length; i++) out[i] = vec[i] * inv;
        return out;
    }
}

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
 * Know 同源本地嵌入：Ollama nomic-embed-text，768 维，L2 归一化。
 *
 * 真机核查（2026-09-23）：
 * - Know LocalEmbeddingProvider 目标 Xenova/nomic-embed-text-v1.5（768d）
 * - Know vectorMath 写明实际向量来自 Ollama nomic-embed-text（单位向量）
 * - 本机 Ollama 已有 nomic-embed-text:latest；Know 目录无 ONNX 权重
 *
 * 取舍：LangChain4j OllamaEmbeddingModel 标准接入，BATCH=4 对齐 Know。
 */
@Component
public class KnowNomicEmbeddingModel implements EmbeddingModel {

    public static final String MODEL_ID = "nomic-embed-text";
    public static final int DIM = 768;
    public static final String SOURCE = "ollama+know-vectorMath";

    private static final Logger log = LoggerFactory.getLogger(KnowNomicEmbeddingModel.class);
    private final EmbeddingModel delegate;

    public KnowNomicEmbeddingModel() {
        this.delegate = OllamaEmbeddingModel.builder()
                .baseUrl("http://127.0.0.1:11434")
                .modelName(MODEL_ID)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    public KnowNomicEmbeddingModel(EmbeddingModel delegate) {
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
            for (Embedding e : delegate.embedAll(clamped).content()) {
                out.add(Embedding.from(l2Normalize(e.vector())));
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.debug("embed n={} ms={} model={}", segments.size(), ms, MODEL_ID);
        return Response.from(out);
    }

    /**
     * Know vectorMath.l2Normalize 同款。
     * 真机：Ollama nomic 本机实测 norm≈22.6（非单位向量），与 Know 注释「已 L2」不一致，
     * 故统一在入口归一化，保证 cosine 内积口径稳定。
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

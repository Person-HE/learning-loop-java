package com.closeloop.application.rag;

import com.closeloop.infrastructure.ai.OllamaBgeEmbeddingModel;
import com.closeloop.infrastructure.rag.QdrantPointWriter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG：切块 → EmbeddingModel(bge-m3-1024) → EmbeddingStore(Qdrant) → TopK 给 LLM。
 * 禁止把整篇文件直接塞上下文当作 RAG。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int CHUNK_MIN = 200;
    private static final int CHUNK_MAX = 500;
    private static final int OVERLAP = 40;
    /** score 下限：实测无关域也到 0.8+，0.35 只挡极弱噪声；后续用相关段占比再调 */
    private static final double MIN_SCORE = 0.35;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final com.closeloop.infrastructure.rag.QdrantPointWriter writer;

    public RagService(OllamaBgeEmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store,
                      com.closeloop.infrastructure.rag.QdrantPointWriter writer) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.writer = writer;
    }

    public record Chunk(String id, String docId, String kpId, int index, String heading, String text) {}
    public record Hit(String id, String docId, String heading, String text, double score) {}
    public record IndexResult(String docId, int chunks, boolean skipped) {}

    public List<Chunk> chunkMarkdown(String docId, String kpId, String markdown) {
        List<Chunk> out = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) return out;
        String[] lines = markdown.split("\n", -1);
        StringBuilder buf = new StringBuilder();
        String heading = "";
        int idx = 0;
        boolean inFence = false;
        for (String line : lines) {
            if (line.trim().startsWith("```")) inFence = !inFence;
            if (!inFence && line.matches("#{2,3}\\s+.+")) {
                if (buf.length() >= CHUNK_MIN) {
                    out.add(mk(docId, kpId, idx++, heading, buf.toString().trim()));
                    buf.setLength(0);
                }
                heading = line.replaceFirst("^#{2,3}\\s+", "").trim();
            }
            buf.append(line).append('\n');
            if (!inFence && buf.length() >= CHUNK_MAX) {
                String text = buf.toString().trim();
                out.add(mk(docId, kpId, idx++, heading, text));
                String tail = text.length() > OVERLAP ? text.substring(text.length() - OVERLAP) : text;
                buf.setLength(0);
                buf.append(tail).append('\n');
            }
        }
        if (buf.length() >= 20) {
            out.add(mk(docId, kpId, idx++, heading, buf.toString().trim()));
        }
        return out;
    }

    private static Chunk mk(String docId, String kpId, int idx, String heading, String text) {
        return new Chunk(docId + "#" + idx, docId, kpId, idx, heading, text);
    }

    /** 批量写入 Qdrant（稳定 point id，重复索引覆盖不膨胀） */
    public IndexResult indexChunks(String docId, List<Chunk> chunks) {
        if (chunks.isEmpty()) return new IndexResult(docId, 0, true);
        try {
            writer.ensureCollection(OllamaBgeEmbeddingModel.DIM);
            List<QdrantPointWriter.Point> points = new ArrayList<>(chunks.size());
            for (Chunk c : chunks) {
                List<TextSegment> one = List.of(TextSegment.from(c.text()));
                float[] vec = embeddingModel.embedAll(one).content().get(0).vector();
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("chunkId", c.id());
                payload.put("docId", c.docId());
                payload.put("kpId", c.kpId() == null ? "" : c.kpId());
                payload.put("heading", c.heading() == null ? "" : c.heading());
                payload.put("text", c.text());
                points.add(new QdrantPointWriter.Point(c.id(), vec, payload));
            }
            writer.upsert(points);
            return new IndexResult(docId, chunks.size(), false);
        } catch (Exception e) {
            throw new IllegalStateException("向量写入失败: " + e.getMessage(), e);
        }
    }

    public List<Hit> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Embedding q = embeddingModel.embed(query).content();
        try {
            List<Map<String, Object>> rows = writer.search(q.vector(), topK, MIN_SCORE);
            List<Hit> out = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Map<String, Object> r : rows) {
                String id = String.valueOf(r.get("chunkId"));
                if (id.isEmpty() || "null".equals(id) || !seen.add(id)) continue;
                out.add(new Hit(
                        id,
                        String.valueOf(r.get("docId")),
                        String.valueOf(r.get("heading")),
                        String.valueOf(r.get("text")),
                        ((Number) r.get("score")).doubleValue()
                ));
            }
            return out;
        } catch (Exception e) {
            log.error("向量检索失败: {}", e.getMessage());
            throw new IllegalStateException("向量检索失败: " + e.getMessage(), e);
        }
    }

    public IndexResult indexDoc(String docId, String kpId, String domain, String title, String markdown) {
        String hash = sha256(markdown);
        List<Chunk> chunks = chunkMarkdown(docId, kpId, markdown);
        return indexChunks(docId, chunks);
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}

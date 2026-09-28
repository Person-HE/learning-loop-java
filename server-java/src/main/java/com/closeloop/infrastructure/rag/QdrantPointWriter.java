package com.closeloop.infrastructure.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Qdrant REST 写入（稳定 point id）。
 *
 * 真实问题（2026-09-23）：EmbeddingStore.addAll 每次生成随机 UUID，
 * 全量 reindex 后 points=13734（201 文档）——重复向量、检索重复命中。
 * 取舍：索引用 REST upsert + UUID.nameUUIDFromBytes(chunkId)；
 *      检索仍走 LangChain4j EmbeddingStore（ANN）。
 */
@Component
public class QdrantPointWriter {

    private final String base;
    private final String collection;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper om = new ObjectMapper();

    public QdrantPointWriter(
            @Value("${app.vector.qdrant-host:127.0.0.1}") String host,
            @Value("${app.vector.qdrant-port:6333}") int restPort,
            @Value("${app.vector.collection:learning_chunks}") String collection) {
        this.base = "http://" + host + ":" + restPort;
        this.collection = collection;
    }

    public record Point(String id, float[] vector, Map<String, Object> payload) {}

    /** 稳定 UUID：同一 chunkId 重复索引覆盖，不膨胀 */
    public static UUID stableId(String chunkId) {
        return UUID.nameUUIDFromBytes(("ll:" + chunkId).getBytes(StandardCharsets.UTF_8));
    }

    public int upsert(List<Point> points) throws Exception {
        if (points.isEmpty()) return 0;
        List<Map<String, Object>> arr = new ArrayList<>(points.size());
        for (Point p : points) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", stableId(p.id()).toString());
            List<Float> vec = new ArrayList<>(p.vector().length);
            for (float v : p.vector()) vec.add(v);
            m.put("vector", vec);
            m.put("payload", p.payload());
            arr.add(m);
        }
        String body = om.writeValueAsString(Map.of("points", arr));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/collections/" + collection + "/points?wait=true"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("qdrant upsert HTTP " + resp.statusCode() + " " + resp.body());
        }
        return points.size();
    }

    public void ensureCollection(int dim) throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(base + "/collections/" + collection))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        HttpResponse<String> g = http.send(get, HttpResponse.BodyHandlers.ofString());
        if (g.statusCode() == 200) return;
        String create = "{\"vectors\":{\"size\":" + dim + ",\"distance\":\"Cosine\"}}";
        HttpRequest post = HttpRequest.newBuilder()
                .uri(URI.create(base + "/collections/" + collection))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(create))
                .build();
        HttpResponse<String> c = http.send(post, HttpResponse.BodyHandlers.ofString());
        if (c.statusCode() / 100 != 2) {
            throw new IllegalStateException("qdrant create collection " + c.statusCode() + " " + c.body());
        }
    }

    public long countPoints() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(base + "/collections/" + collection))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> g = http.send(get, HttpResponse.BodyHandlers.ofString());
        var node = om.readTree(g.body());
        return node.path("result").path("points_count").asLong(0);
    }

    /** 向量检索（与 upsert 同一 payload 契约，避免 EmbeddingStore 自有格式不兼容） */
    public List<Map<String, Object>> search(float[] vector, int topK, double minScore) throws Exception {
        List<Float> vec = new ArrayList<>(vector.length);
        for (float v : vector) vec.add(v);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vec);
        body.put("limit", topK);
        body.put("score_threshold", minScore);
        body.put("with_payload", true);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/collections/" + collection + "/points/search"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("qdrant search HTTP " + resp.statusCode() + " " + resp.body());
        }
        var arr = om.readTree(resp.body()).path("result");
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr.isArray()) {
            for (var n : arr) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("score", n.path("score").asDouble());
                var p = n.path("payload");
                row.put("chunkId", p.path("chunkId").asText(""));
                row.put("docId", p.path("docId").asText(""));
                row.put("heading", p.path("heading").asText(""));
                row.put("text", p.path("text").asText(""));
                out.add(row);
            }
        }
        return out;
    }
}

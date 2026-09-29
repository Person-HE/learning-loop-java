package com.closeloop.infrastructure.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 嵌入自检：LangChain4j 调用本机 Ollama bge-m3。
 * 模型文件已复制到 server-java/models/；推理走本机 Ollama 同款权重。
 * 运行：java -cp target/classes com.closeloop.infrastructure.ai.EmbedSmoke
 */
public final class EmbedSmoke {

    public static void main(String[] args) {
        Path models = Path.of("models");
        System.out.println("models_dir_exists=" + Files.isDirectory(models));
        try (var s = Files.list(models)) {
            s.forEach(p -> System.out.println("  " + p.getFileName()));
        } catch (Exception e) {
            System.out.println("list_err=" + e.getMessage());
        }

        EmbeddingModel model = OllamaEmbeddingModel.builder()
                .baseUrl("http://127.0.0.1:11434")
                .modelName("bge-m3")
                .timeout(Duration.ofSeconds(30))
                .build();

        Response<List<Embedding>> r = model.embedAll(List.of(TextSegment.from("Redis 缓存击穿")));
        Embedding e = r.content().get(0);
        float[] v = e.vector();
        double n = 0;
        for (float x : v) n += x * x;
        System.out.println("dim=" + v.length + " norm=" + Math.sqrt(n));
        if (v.length != OllamaBgeEmbeddingModel.DIM) {
            System.exit(2);
        }
    }

    private EmbedSmoke() {}
}

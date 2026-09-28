package com.closeloop.infrastructure.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量库：Qdrant + LangChain4j EmbeddingStore。
 * 问题：向量曾进 MySQL BLOB——无 ANN、非企业分层。
 * 决策：业务=MySQL/MyBatis；向量=Qdrant。
 */
@Configuration
public class VectorStoreConfig {

    /**
     * 问题：QdrantEmbeddingStore 走 gRPC，端口是 6334；填 6333(REST) 会报 INTERNAL: http2 exception。
     * 决策：默认 gRPC 6334；collection 需 dim=768 Cosine（启动前 REST 创建或自动建）。
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${app.vector.qdrant-host:127.0.0.1}") String host,
            @Value("${app.vector.qdrant-grpc-port:6334}") int port,
            @Value("${app.vector.collection:learning_chunks}") String collection) {
        return QdrantEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName(collection)
                .build();
    }
}

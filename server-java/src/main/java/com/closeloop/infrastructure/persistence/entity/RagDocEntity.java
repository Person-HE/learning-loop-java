package com.closeloop.infrastructure.persistence.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** rag_doc / rag_chunk */
public class RagDocEntity {
    private String docId;
    private String kpId;
    private String domain;
    private String title;
    private String contentHash;
    private Integer chunkTotal;
    private LocalDateTime embeddedAt;

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Integer getChunkTotal() { return chunkTotal; }
    public void setChunkTotal(Integer chunkTotal) { this.chunkTotal = chunkTotal; }
    public LocalDateTime getEmbeddedAt() { return embeddedAt; }
    public void setEmbeddedAt(LocalDateTime embeddedAt) { this.embeddedAt = embeddedAt; }
}

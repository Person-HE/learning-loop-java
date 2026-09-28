package com.closeloop.infrastructure.persistence.entity;

/** rag_chunk 表 */
public class RagChunkEntity {
    private String chunkId;
    private String docId;
    private String kpId;
    private Integer chunkIndex;
    private String heading;
    private String text;
    private Integer tokenEst;
    private byte[] embedding;
    private String embedModel;

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getTokenEst() { return tokenEst; }
    public void setTokenEst(Integer tokenEst) { this.tokenEst = tokenEst; }
    public byte[] getEmbedding() { return embedding; }
    public void setEmbedding(byte[] embedding) { this.embedding = embedding; }
    public String getEmbedModel() { return embedModel; }
    public void setEmbedModel(String embedModel) { this.embedModel = embedModel; }
}

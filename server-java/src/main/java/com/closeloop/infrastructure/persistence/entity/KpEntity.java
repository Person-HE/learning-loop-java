package com.closeloop.infrastructure.persistence.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** kp 表 — 知识点注册（KB + LC） */
public class KpEntity {
    private String id;
    private String domain;
    private String chapter;
    private String title;
    private Integer difficulty;
    private Integer hot;
    private String category;
    private String path;
    private String kbStatus;
    private Boolean isLc;
    private String metaJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getChapter() { return chapter; }
    public void setChapter(String chapter) { this.chapter = chapter; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public Integer getHot() { return hot; }
    public void setHot(Integer hot) { this.hot = hot; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getKbStatus() { return kbStatus; }
    public void setKbStatus(String kbStatus) { this.kbStatus = kbStatus; }
    public Boolean getIsLc() { return isLc; }
    public void setIsLc(Boolean isLc) { this.isLc = isLc; }
    public String getMetaJson() { return metaJson; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

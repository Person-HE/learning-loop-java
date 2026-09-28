package com.closeloop.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** kp_state 表 — SM-2 / 可提取性热路径状态 */
public class KpStateEntity {
    private String kpId;
    private String status;
    private BigDecimal ease;
    private Integer intervalDays;
    private LocalDate dueDate;
    private BigDecimal stability;
    private Integer lapses;
    private Integer lastScore;
    private Integer reviews;
    private LocalDate lastReviewDate;
    private String planJson;
    private Long version;
    private LocalDateTime updatedAt;

    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getEase() { return ease; }
    public void setEase(BigDecimal ease) { this.ease = ease; }
    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getStability() { return stability; }
    public void setStability(BigDecimal stability) { this.stability = stability; }
    public Integer getLapses() { return lapses; }
    public void setLapses(Integer lapses) { this.lapses = lapses; }
    public Integer getLastScore() { return lastScore; }
    public void setLastScore(Integer lastScore) { this.lastScore = lastScore; }
    public Integer getReviews() { return reviews; }
    public void setReviews(Integer reviews) { this.reviews = reviews; }
    public LocalDate getLastReviewDate() { return lastReviewDate; }
    public void setLastReviewDate(LocalDate lastReviewDate) { this.lastReviewDate = lastReviewDate; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

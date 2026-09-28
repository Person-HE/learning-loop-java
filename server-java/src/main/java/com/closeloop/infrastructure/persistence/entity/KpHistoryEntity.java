package com.closeloop.infrastructure.persistence.entity;

import java.time.LocalDate;

/** kp_history 表 */
public class KpHistoryEntity {
    private Long id;
    private String kpId;
    private LocalDate reviewDate;
    private Integer score;
    private Integer q;
    private Double stability;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public LocalDate getReviewDate() { return reviewDate; }
    public void setReviewDate(LocalDate reviewDate) { this.reviewDate = reviewDate; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getQ() { return q; }
    public void setQ(Integer q) { this.q = q; }
    public Double getStability() { return stability; }
    public void setStability(Double stability) { this.stability = stability; }
}

package com.closeloop.infrastructure.persistence.entity;

import java.time.LocalDate;

/** session_day 表 */
public class SessionDayEntity {
    private LocalDate sessionDate;
    private Integer cnt;
    private Integer avgScore;
    private Integer newG;
    private Integer resG;
    private Integer minScore;
    private Boolean spoken;
    private String mode;

    public LocalDate getSessionDate() { return sessionDate; }
    public void setSessionDate(LocalDate sessionDate) { this.sessionDate = sessionDate; }
    public Integer getCnt() { return cnt; }
    public void setCnt(Integer cnt) { this.cnt = cnt; }
    public Integer getAvgScore() { return avgScore; }
    public void setAvgScore(Integer avgScore) { this.avgScore = avgScore; }
    public Integer getNewG() { return newG; }
    public void setNewG(Integer newG) { this.newG = newG; }
    public Integer getResG() { return resG; }
    public void setResG(Integer resG) { this.resG = resG; }
    public Integer getMinScore() { return minScore; }
    public void setMinScore(Integer minScore) { this.minScore = minScore; }
    public Boolean getSpoken() { return spoken; }
    public void setSpoken(Boolean spoken) { this.spoken = spoken; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}

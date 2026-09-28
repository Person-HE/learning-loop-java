package com.closeloop.infrastructure.persistence.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** queue_item 表 */
public class QueueItemEntity {
    private String id;
    private String kpId;
    private String kind;
    private String reason;
    private String questionsJson;
    private LocalDate createdOn;
    private LocalDateTime consumedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getQuestionsJson() { return questionsJson; }
    public void setQuestionsJson(String questionsJson) { this.questionsJson = questionsJson; }
    public LocalDate getCreatedOn() { return createdOn; }
    public void setCreatedOn(LocalDate createdOn) { this.createdOn = createdOn; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(LocalDateTime consumedAt) { this.consumedAt = consumedAt; }
}

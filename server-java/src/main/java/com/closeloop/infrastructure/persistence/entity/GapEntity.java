package com.closeloop.infrastructure.persistence.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** gap 表 */
public class GapEntity {
    private String id;
    private String kpId;
    private String label;
    private String description;
    private LocalDateTime createdAt;
    private LocalDate resolvedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDate getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDate resolvedAt) { this.resolvedAt = resolvedAt; }
}

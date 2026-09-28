package com.closeloop.state.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 知识缺口：AI 评分产出入库，同知识点同标签去重；消灭需实测 ≥75 分（防自欺） */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Gap {

    public String id;
    public String kpId;
    public String title;
    /** 概念混淆 | 因果链断裂 | 边界缺失 | 术语不准 | 表达卡顿 | 空白 */
    public String label;
    public String detail = "";
    public String createdAt;
    public String resolvedAt;
    public String recurredAt;
}

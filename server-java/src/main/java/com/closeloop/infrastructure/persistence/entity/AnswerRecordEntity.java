package com.closeloop.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/** answer_record 表 */
public class AnswerRecordEntity {
    private String id;
    private String kpId;
    private String questionId;
    private String question;
    private String answerText;
    private String code;
    private String think;
    private Boolean spoken;
    private Integer score;
    private String level;
    private String verdict;
    private String profile;
    private String diagnoseJson;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getThink() { return think; }
    public void setThink(String think) { this.think = think; }
    public Boolean getSpoken() { return spoken; }
    public void setSpoken(Boolean spoken) { this.spoken = spoken; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public String getDiagnoseJson() { return diagnoseJson; }
    public void setDiagnoseJson(String diagnoseJson) { this.diagnoseJson = diagnoseJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

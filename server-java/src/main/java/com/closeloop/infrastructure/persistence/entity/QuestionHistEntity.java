package com.closeloop.infrastructure.persistence.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** question_hist 表 — 同题遗忘调度 */
public class QuestionHistEntity {
    private String kpId;
    private String questionId;
    private String question;
    private String answerPointsJson;
    private String point;
    private Integer score;
    private Integer tries;
    private LocalDate dueDate;
    private LocalDateTime lastAt;

    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswerPointsJson() { return answerPointsJson; }
    public void setAnswerPointsJson(String answerPointsJson) { this.answerPointsJson = answerPointsJson; }
    public String getPoint() { return point; }
    public void setPoint(String point) { this.point = point; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getTries() { return tries; }
    public void setTries(Integer tries) { this.tries = tries; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDateTime getLastAt() { return lastAt; }
    public void setLastAt(LocalDateTime lastAt) { this.lastAt = lastAt; }
}

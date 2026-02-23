package com.medical.qc.entity;

import java.time.LocalDateTime;

public class HemorrhageRecord {
    private Long id;
    private Long userId;
    private String patientName;
    private String examId;
    private String imagePath;
    private String prediction;
    private String confidenceLevel;
    private Float hemorrhageProbability;
    private Float noHemorrhageProbability;
    private Float analysisDuration;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getExamId() {
        return examId;
    }

    public void setExamId(String examId) {
        this.examId = examId;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public Float getHemorrhageProbability() {
        return hemorrhageProbability;
    }

    public void setHemorrhageProbability(Float hemorrhageProbability) {
        this.hemorrhageProbability = hemorrhageProbability;
    }

    public Float getNoHemorrhageProbability() {
        return noHemorrhageProbability;
    }

    public void setNoHemorrhageProbability(Float noHemorrhageProbability) {
        this.noHemorrhageProbability = noHemorrhageProbability;
    }

    public Float getAnalysisDuration() {
        return analysisDuration;
    }

    public void setAnalysisDuration(Float analysisDuration) {
        this.analysisDuration = analysisDuration;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

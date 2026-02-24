package com.medical.qc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 脑出血检测历史记录实体。
 */
@TableName("hemorrhage_records")
public class HemorrhageRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
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
    private Boolean midlineShift;
    private Float shiftScore;
    private String midlineDetail;
    private Boolean ventricleIssue;
    private String ventricleDetail;
    private String device;
    private String rawResultJson;
    private LocalDateTime updatedAt;

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

    public Boolean getMidlineShift() {
        return midlineShift;
    }

    public void setMidlineShift(Boolean midlineShift) {
        this.midlineShift = midlineShift;
    }

    public Float getShiftScore() {
        return shiftScore;
    }

    public void setShiftScore(Float shiftScore) {
        this.shiftScore = shiftScore;
    }

    public String getMidlineDetail() {
        return midlineDetail;
    }

    public void setMidlineDetail(String midlineDetail) {
        this.midlineDetail = midlineDetail;
    }

    public Boolean getVentricleIssue() {
        return ventricleIssue;
    }

    public void setVentricleIssue(Boolean ventricleIssue) {
        this.ventricleIssue = ventricleIssue;
    }

    public String getVentricleDetail() {
        return ventricleDetail;
    }

    public void setVentricleDetail(String ventricleDetail) {
        this.ventricleDetail = ventricleDetail;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getRawResultJson() {
        return rawResultJson;
    }

    public void setRawResultJson(String rawResultJson) {
        this.rawResultJson = rawResultJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
